package com.pgoptima.analyticsservice.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.pgoptima.analyticsservice.client.UserServiceClient;
import com.pgoptima.analyticsservice.config.PlanAnalyzerProperties;
import com.pgoptima.shareddto.enums.ImpactLevel;
import com.pgoptima.shareddto.enums.RecommendationType;
import com.pgoptima.shareddto.response.ConnectionDTO;
import com.pgoptima.shareddto.response.Recommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.SubSelect;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlanAnalyzer {

    private final PlanAnalyzerProperties properties;
    private final UserServiceClient userServiceClient;

    private static class AnalysisContext {
        String originalQuery;
        List<JsonNode> scanNodes = new ArrayList<>();
        List<JsonNode> joinNodes = new ArrayList<>();
        List<JsonNode> subqueryNodes = new ArrayList<>();
        PlanMetrics metrics = new PlanMetrics();
        long rootActualRows;
    }

    public List<Recommendation> analyze(JsonNode plan, String originalQuery, ConnectionDTO connection, String authHeader) {
        List<Recommendation> recommendations = new ArrayList<>();
        if (plan == null || plan.isMissingNode()) {
            recommendations.add(createErrorRecommendation("План выполнения отсутствует или повреждён"));
            return recommendations;
        }

        AnalysisContext context = new AnalysisContext();
        context.originalQuery = originalQuery;
        collectMetrics(plan, context.metrics);
        context.rootActualRows = extractRootActualRows(plan);
        collectContextInfo(plan, context);

        analyzeSeqScans(recommendations, context, connection, authHeader);
        analyzeJoinIndexes(recommendations, context, connection, authHeader);
        analyzeSorts(recommendations, context);
        analyzeMissingLimit(recommendations, context);
        analyzeDiskTemporaryFiles(recommendations, context);
        analyzeNestedLoops(recommendations, context);
        analyzeDataTypeMismatch(recommendations, context.originalQuery);
        analyzeParallelQueryOpportunity(recommendations, context);
        analyzePartitioningOpportunity(recommendations, context, connection, authHeader);
        analyzeJitCompilation(recommendations, context);
        analyzeGroupByOptimization(recommendations, context, connection, authHeader);
        analyzeFunctionInWhere(recommendations, context.originalQuery);
        analyzeOutdatedStats(recommendations, context, connection, authHeader);

        return sortAndDeduplicate(recommendations);
    }

    private long extractRootActualRows(JsonNode plan) {
        if (plan == null) return 0;
        if (plan.has("Actual Rows")) return plan.get("Actual Rows").asLong();
        if (plan.has("Plan Rows")) return plan.get("Plan Rows").asLong();
        return 0;
    }

    private long getTableRowCount(ConnectionDTO connection, String tableName) {
        String url = String.format("jdbc:postgresql://%s:%d/%s?sslmode=%s",
                connection.getHost(), connection.getPort(), connection.getDatabase(),
                connection.getSslMode() != null ? connection.getSslMode() : "disable");
        try (Connection conn = DriverManager.getConnection(url, connection.getUsername(), connection.getPassword())) {
            String sql = "SELECT reltuples::bigint FROM pg_class WHERE relname = ? AND relkind = 'r'";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tableName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            log.warn("Не удалось получить количество строк для таблицы {}: {}", tableName, e.getMessage());
        }
        return 0;
    }

    private void analyzeSeqScans(List<Recommendation> recommendations, AnalysisContext ctx, ConnectionDTO connection, String authHeader) {
        for (JsonNode node : ctx.scanNodes) {
            String nodeType = getNodeText(node, "Node Type");
            if ("Seq Scan".equals(nodeType)) {
                long estimatedRowsAfterFilter = getNodeLong(node, "Plan Rows");
                String table = getNodeText(node, "Relation Name");
                long totalTableRows = getTableRowCount(connection, table);
                if (totalTableRows <= 0) totalTableRows = estimatedRowsAfterFilter;
                String filter = getNodeText(node, "Filter");
                if (table == null || table.isEmpty() || table.equals("?")) continue;
                if (!filter.isEmpty()) {
                    List<String> cols = extractColumnsFromFilter(filter);
                    if (!cols.isEmpty()) {
                        String col = cols.get(0);
                        double selectivity = (double) estimatedRowsAfterFilter / totalTableRows;
                        if (selectivity > properties.getIndexSelectivityThreshold()) {
                            log.debug("Селективность {} > порога {}, пропускаем индекс для {}.{}", selectivity, properties.getIndexSelectivityThreshold(), table, col);
                            continue;
                        }
                        double improvement = (1.0 - selectivity) * 100.0;
                        improvement = Math.min(95.0, Math.max(20.0, improvement));
                        String indexCmd = String.format("CREATE INDEX IF NOT EXISTS idx_%s_%s ON %s(%s);",
                                table, col.toLowerCase(), table, col);
                        addRecommendation(recommendations, RecommendationType.CREATE_INDEX,
                                String.format("Таблица '%s' содержит %d строк, выполняется полное сканирование. Создайте индекс для ускорения фильтрации.", table, totalTableRows),
                                ImpactLevel.HIGH, improvement, indexCmd);
                    }
                }
            }
        }
    }

    private void analyzeJoinIndexes(List<Recommendation> recommendations, AnalysisContext ctx, ConnectionDTO connection, String authHeader) {
        for (JsonNode joinNode : ctx.joinNodes) {
            String joinType = getNodeText(joinNode, "Node Type");
            if (!joinType.contains("Join")) continue;
            String hashCond = getNodeText(joinNode, "Hash Cond");
            String mergeCond = getNodeText(joinNode, "Merge Cond");
            String joinFilter = getNodeText(joinNode, "Join Filter");
            String condition = null;
            if (!hashCond.isEmpty()) condition = hashCond;
            else if (!mergeCond.isEmpty()) condition = mergeCond;
            else if (!joinFilter.isEmpty()) condition = joinFilter;
            if (condition == null) continue;

            List<String> qualifiedCols = extractQualifiedColumns(condition);
            if (qualifiedCols.isEmpty()) continue;

            JsonNode outer = getChildPlan(joinNode, 0);
            JsonNode inner = getChildPlan(joinNode, 1);
            String leftAlias = findAliasInPlan(outer);
            String rightAlias = findAliasInPlan(inner);
            String leftTable = findRelationNameInPlan(outer);
            String rightTable = findRelationNameInPlan(inner);

            Map<String, List<String>> tableColumns = new HashMap<>();
            for (String qcol : qualifiedCols) {
                String[] parts = qcol.split("\\.");
                String alias = parts.length == 2 ? parts[0] : null;
                String colName = parts.length == 2 ? parts[1] : parts[0];
                String tableName = null;
                if (alias != null) {
                    if (alias.equalsIgnoreCase(leftAlias)) tableName = leftTable;
                    else if (alias.equalsIgnoreCase(rightAlias)) tableName = rightTable;
                }
                if (tableName == null) continue;
                tableColumns.computeIfAbsent(tableName, k -> new ArrayList<>()).add(colName);
            }

            for (Map.Entry<String, List<String>> entry : tableColumns.entrySet()) {
                String table = entry.getKey();
                List<String> cols = entry.getValue();
                if (table == null || cols.isEmpty()) continue;
                Set<String> uniqueCols = new LinkedHashSet<>(cols);
                String columnsStr = String.join("_", uniqueCols).toLowerCase();
                String indexName = "idx_" + table + "_" + columnsStr;
                String indexCmd = String.format("CREATE INDEX IF NOT EXISTS %s ON %s(%s);", indexName, table, String.join(", ", uniqueCols));
                addRecommendation(recommendations, RecommendationType.CREATE_INDEX,
                        "Для ускорения операций JOIN создайте индекс на таблице " + table + " по столбцам: " + String.join(", ", uniqueCols),
                        ImpactLevel.MEDIUM, 30.0, indexCmd);
            }
        }
    }

    private void analyzeSorts(List<Recommendation> recommendations, AnalysisContext ctx) {
        if (ctx.metrics.sortCount == 0) return;
        boolean diskSort = ctx.metrics.diskSortCount > 0;
        double improvement = diskSort ? 70.0 : 35.0;
        String suggestion = diskSort ? "Сортировка выполняется на диске. Увеличьте work_mem." : "Большая сортировка без использования индекса. Увеличьте work_mem.";
        addRecommendation(recommendations, RecommendationType.INCREASE_WORK_MEM, suggestion,
                diskSort ? ImpactLevel.HIGH : ImpactLevel.MEDIUM, improvement, "SET work_mem = '64MB';");
    }

    private void analyzeMissingLimit(List<Recommendation> recommendations, AnalysisContext ctx) {
        long rootRows = ctx.rootActualRows;
        if (rootRows > 1000 && ctx.originalQuery != null && !ctx.originalQuery.toUpperCase().contains("LIMIT")) {
            double improvement = Math.min(90.0, (1.0 - 100.0 / rootRows) * 100.0);
            String modifiedQuery = addLimitToQuery(ctx.originalQuery, 100);
            addRecommendation(recommendations, RecommendationType.ADD_LIMIT,
                    String.format("Запрос возвращает %d строк. Добавьте LIMIT 100.", rootRows),
                    ImpactLevel.MEDIUM, improvement, modifiedQuery);
        }
    }

    private void analyzeDiskTemporaryFiles(List<Recommendation> recommendations, AnalysisContext ctx) {
        if (ctx.metrics.tempWrittenBlocks > properties.getHighBufferThreshold()) {
            addRecommendation(recommendations, RecommendationType.INCREASE_WORK_MEM,
                    "Запрос использует временные файлы на диске (" + ctx.metrics.tempWrittenBlocks + " блоков). Увеличьте work_mem.",
                    ImpactLevel.HIGH, 40.0, "SET work_mem = '128MB';");
        }
    }

    // ==================== НОВЫЕ АЛГОРИТМЫ ====================

    private void analyzeNestedLoops(List<Recommendation> recommendations, AnalysisContext ctx) {
        if (ctx.metrics.nestedLoopCount == 0) return;

        boolean hasCorrelatedSubquery = ctx.metrics.correlatedSubqueryCount > 0;
        if (!hasCorrelatedSubquery && ctx.originalQuery != null) {
            hasCorrelatedSubquery = detectCorrelatedSubquery(ctx.originalQuery);
        }

        if (hasCorrelatedSubquery) {
            double improvement = 85.0;
            String afterQuery = suggestSemiJoinRewrite(ctx.originalQuery);
            addRecommendation(recommendations, RecommendationType.REWRITE_JOIN_TO_SEMI_JOIN,
                    "Обнаружены коррелированные подзапросы с Nested Loop, которые многократно выполняются для каждой строки. Перепишите запрос с использованием SEMI JOIN (EXISTS → INNER JOIN с DISTINCT) для значительного ускорения.",
                    ImpactLevel.HIGH, improvement, afterQuery);
        } else if (ctx.metrics.nestedLoopCount > 2 && ctx.metrics.hashJoinCount == 0 && ctx.metrics.mergeJoinCount == 0) {
            addRecommendation(recommendations, RecommendationType.REWRITE_JOIN_TO_SEMI_JOIN,
                    "Запрос использует только Nested Loop без альтернативных стратегий соединения. Рассмотрите возможность переписывания запроса или увеличения work_mem для использования Hash Join.",
                    ImpactLevel.MEDIUM, 40.0, "SET work_mem = '128MB'; -- или перепишите запрос");
        }
    }

    private boolean detectCorrelatedSubquery(String sql) {
        if (sql == null) return false;
        String upper = sql.toUpperCase();
        Pattern pattern = Pattern.compile("(EXISTS|IN)\\s*\\(\\s*SELECT\\s+.+?\\s+WHERE\\s+.+?\\s*=\\s*\\w+\\.\\w+", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        return pattern.matcher(sql).find();
    }

    private String suggestSemiJoinRewrite(String sql) {
        if (sql == null) return sql;
        if (sql.toUpperCase().contains("EXISTS")) {
            return sql + " -- ПОДСКАЗКА: замените EXISTS на INNER JOIN (SELECT DISTINCT ...)";
        }
        return sql;
    }

    private void analyzeDataTypeMismatch(List<Recommendation> recommendations, String sql) {
        if (sql == null) return;

        Pattern explicitCastPattern = Pattern.compile("(\\w+)\\s*::\\s*(\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher m = explicitCastPattern.matcher(sql);
        List<String> foundCasts = new ArrayList<>();
        while (m.find()) {
            foundCasts.add(m.group(1) + "::" + m.group(2));
        }

        if (!foundCasts.isEmpty()) {
            addRecommendation(recommendations, RecommendationType.FIX_DATA_TYPE_MISMATCH,
                    "Обнаружены явные приведения типов (" + String.join(", ", foundCasts) + "), которые могут препятствовать использованию индексов. Измените тип данных столбца или удалите приведение.",
                    ImpactLevel.MEDIUM, 25.0, "ALTER TABLE ... ALTER COLUMN ... TYPE ...; -- или удалите приведение в запросе");
        }

        Pattern mismatchPattern = Pattern.compile("(\\w+)\\s*=\\s*'(\\d+)'", Pattern.CASE_INSENSITIVE);
        Matcher m2 = mismatchPattern.matcher(sql);
        if (m2.find()) {
            addRecommendation(recommendations, RecommendationType.FIX_DATA_TYPE_MISMATCH,
                    "Сравнение числового столбца со строковым литералом (например, " + m2.group(1) + " = '" + m2.group(2) + "') вызывает неявное приведение типов и отключает индекс. Уберите кавычки у числа.",
                    ImpactLevel.MEDIUM, 20.0, null);
        }
    }

    private void analyzeParallelQueryOpportunity(List<Recommendation> recommendations, AnalysisContext ctx) {
        boolean isHeavyQuery = ctx.metrics.totalCost > 10000 ||
                ctx.metrics.actualRows > 1000000 ||
                ctx.metrics.seqScanCount > 0;

        boolean hasAggregation = ctx.metrics.aggregateCount > 0;
        boolean hasLargeScan = ctx.metrics.seqScanCount > 0 && ctx.metrics.actualRows > 500000;

        if (isHeavyQuery && (hasAggregation || hasLargeScan)) {
            if (ctx.metrics.parallelWorkerCount == 0) {
                addRecommendation(recommendations, RecommendationType.ENABLE_PARALLEL_QUERY,
                        "Запрос обрабатывает большой объём данных (" + formatNumber(ctx.metrics.actualRows) + " строк, стоимость " +
                                String.format("%.0f", ctx.metrics.totalCost) + "), но не использует параллельные workers. Настройте параметры параллелизма: max_parallel_workers_per_gather, parallel_setup_cost, parallel_tuple_cost.",
                        ImpactLevel.HIGH, 60.0, "ALTER SYSTEM SET max_parallel_workers_per_gather = 4; SELECT pg_reload_conf();");
            } else if (ctx.metrics.parallelWorkerCount < 2) {
                addRecommendation(recommendations, RecommendationType.ENABLE_PARALLEL_QUERY,
                        "Запрос использует только " + ctx.metrics.parallelWorkerCount + " параллельных worker'а, хотя объём данных (" +
                                formatNumber(ctx.metrics.actualRows) + " строк) позволяет увеличить их количество.",
                        ImpactLevel.MEDIUM, 30.0, "ALTER SYSTEM SET max_parallel_workers_per_gather = 4;");
            }
        }
    }

    private void analyzePartitioningOpportunity(List<Recommendation> recommendations, AnalysisContext ctx, ConnectionDTO connection, String authHeader) {
        Set<String> largeTables = new HashSet<>();
        for (JsonNode node : ctx.scanNodes) {
            String table = getNodeText(node, "Relation Name");
            long rows = getNodeLong(node, "Plan Rows");
            if (table != null && !table.isEmpty() && rows > properties.getPartitionSizeThreshold()) {
                largeTables.add(table);
            }
        }

        boolean hasDateFilter = false;
        if (ctx.originalQuery != null) {
            hasDateFilter = ctx.originalQuery.toLowerCase().contains("date") ||
                    ctx.originalQuery.toLowerCase().contains("book_date") ||
                    ctx.originalQuery.toLowerCase().contains("actual_departure");
        }

        if (!largeTables.isEmpty() && hasDateFilter) {
            for (String table : largeTables) {
                addRecommendation(recommendations, RecommendationType.SUGGEST_PARTITIONING,
                        "Таблица '" + table + "' содержит " + formatNumber(getTableRowCount(connection, table)) +
                                " строк и часто фильтруется по дате. Рассмотрите партиционирование по диапазону (RANGE PARTITION BY дата) для улучшения производительности запросов и обслуживания.",
                        ImpactLevel.HIGH, 75.0, "CREATE TABLE " + table + "_partitioned PARTITION BY RANGE (book_date);");
            }
        } else if (!largeTables.isEmpty()) {
            for (String table : largeTables) {
                addRecommendation(recommendations, RecommendationType.SUGGEST_PARTITIONING,
                        "Таблица '" + table + "' содержит " + formatNumber(getTableRowCount(connection, table)) +
                                " строк. Партиционирование может улучшить производительность запросов, особенно если добавить фильтр по ключу партиционирования.",
                        ImpactLevel.MEDIUM, 50.0, "CREATE TABLE " + table + "_partitioned PARTITION BY RANGE (id);");
            }
        }
    }

    private void analyzeJitCompilation(List<Recommendation> recommendations, AnalysisContext ctx) {
        boolean isOlapQuery = ctx.metrics.aggregateCount > 1 ||
                ctx.metrics.joinCount > 2 ||
                ctx.metrics.totalCost > 50000 ||
                ctx.metrics.actualRows > 1000000;

        if (isOlapQuery) {
            if (!ctx.metrics.jitUsed) {
                addRecommendation(recommendations, RecommendationType.ENABLE_JIT_COMPILATION,
                        "Обнаружен сложный OLAP-запрос с высокой стоимостью выполнения. JIT-компиляция может ускорить выполнение выражений и операций. Включите JIT (доступно с PostgreSQL 11).",
                        ImpactLevel.MEDIUM, 35.0, "ALTER SYSTEM SET jit = on; ALTER SYSTEM SET jit_above_cost = 100000; SELECT pg_reload_conf();");
            }
        }
    }

    private void analyzeGroupByOptimization(List<Recommendation> recommendations, AnalysisContext ctx, ConnectionDTO connection, String authHeader) {
        if (ctx.metrics.aggregateCount == 0) return;

        boolean hasHashAggregate = false;
        boolean hasGroupAggregate = false;
        for (JsonNode node : ctx.scanNodes) {
            String nodeType = getNodeText(node, "Node Type");
            if ("HashAggregate".equals(nodeType)) hasHashAggregate = true;
            if ("GroupAggregate".equals(nodeType)) hasGroupAggregate = true;
        }

        long rowsProcessed = ctx.metrics.actualRows;

        if (hasGroupAggregate && rowsProcessed > 100000) {
            addRecommendation(recommendations, RecommendationType.OPTIMIZE_GROUP_BY,
                    "Обнаружена дорогостоящая агрегация с сортировкой (GroupAggregate). Для ускорения создайте индекс на столбцах GROUP BY или переключитесь на HashAggregate через увеличение work_mem.",
                    ImpactLevel.MEDIUM, 45.0, "SET work_mem = '128MB'; -- или CREATE INDEX ON table(column1, column2);");
        }

        if (hasHashAggregate && rowsProcessed > 5000000) {
            addRecommendation(recommendations, RecommendationType.OPTIMIZE_GROUP_BY,
                    "HashAggregate использует много памяти (" + formatNumber(rowsProcessed) +
                            " строк). Рассмотрите возможность создания материализованного представления или предварительной агрегации данных.",
                    ImpactLevel.MEDIUM, 30.0, "CREATE MATERIALIZED VIEW agg_view AS SELECT ... GROUP BY ...;");
        }
    }

    private void analyzeFunctionInWhere(List<Recommendation> recommendations, String sql) {
        if (sql == null) return;

        Pattern funcInWherePattern = Pattern.compile("WHERE\\s+(\\w+)\\s*\\([^)]+\\)\\s*=", Pattern.CASE_INSENSITIVE);
        Matcher m = funcInWherePattern.matcher(sql);
        Set<String> foundFunctions = new HashSet<>();

        while (m.find()) {
            foundFunctions.add(m.group(1));
        }

        Pattern funcOnColumnPattern = Pattern.compile("(\\w+)\\s*\\(\\s*(\\w+)\\s*\\)", Pattern.CASE_INSENSITIVE);
        Matcher m2 = funcOnColumnPattern.matcher(sql);
        while (m2.find()) {
            foundFunctions.add(m2.group(1));
        }

        if (!foundFunctions.isEmpty()) {
            String funcNames = String.join(", ", foundFunctions);
            addRecommendation(recommendations, RecommendationType.AVOID_FUNCTION_IN_WHERE,
                    "В условии WHERE используется функция " + funcNames +
                            " над столбцом, что отключает использование индекса. Перепишите запрос без функции или создайте функциональный индекс.",
                    ImpactLevel.HIGH, 70.0, "CREATE INDEX idx_expression ON table(" + foundFunctions.iterator().next() + "(column));");
        }
    }

    private void analyzeOutdatedStats(List<Recommendation> recommendations, AnalysisContext ctx, ConnectionDTO connection, String authHeader) {
        if (ctx.metrics.actualRows == 0 || ctx.metrics.plannedRows == 0) return;

        double ratio;
        if (ctx.metrics.actualRows > ctx.metrics.plannedRows) {
            ratio = (double) ctx.metrics.actualRows / ctx.metrics.plannedRows;
        } else {
            ratio = (double) ctx.metrics.plannedRows / ctx.metrics.actualRows;
        }

        if (ratio > 10.0) {
            addRecommendation(recommendations, RecommendationType.UPDATE_STATISTICS,
                    "Планировщик запросов серьёзно ошибся в оценке количества строк (ошибка в " +
                            String.format("%.1f", ratio) + " раз). Статистика таблиц устарела. Выполните ANALYZE для обновления.",
                    ImpactLevel.HIGH, 80.0, "ANALYZE; -- или ANALYZE specific_table;");
        } else if (ratio > 3.0) {
            addRecommendation(recommendations, RecommendationType.UPDATE_STATISTICS,
                    "Статистика устарела (расхождение в " + String.format("%.1f", ratio) +
                            " раз между оценкой и фактическими строками). Выполните ANALYZE.",
                    ImpactLevel.MEDIUM, 40.0, "ANALYZE;");
        }
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private List<String> extractColumnsFromFilter(String filter) {
        List<String> cols = new ArrayList<>();
        if (filter == null || filter.isEmpty()) return cols;
        String normalized = filter.replaceAll("::[a-zA-Z]+", "");
        Pattern p = Pattern.compile("\\b([a-z_][a-z0-9_]*)\\s*(?:=|>|<|>=|<=|LIKE|IN)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(normalized);
        while (m.find()) {
            String col = m.group(1);
            if (!cols.contains(col) && !col.equalsIgnoreCase("null") && !col.equalsIgnoreCase("unknown")) {
                cols.add(col);
            }
        }
        return cols;
    }

    private List<String> extractQualifiedColumns(String condition) {
        List<String> qualifiedCols = new ArrayList<>();
        if (condition == null || condition.isEmpty()) return qualifiedCols;
        try {
            Expression expr = CCJSqlParserUtil.parseCondExpression(condition);
            Deque<Expression> stack = new ArrayDeque<>();
            stack.push(expr);
            while (!stack.isEmpty()) {
                Expression current = stack.pop();
                if (current instanceof Column) {
                    Column col = (Column) current;
                    String alias = col.getTable() != null ? col.getTable().getName() : null;
                    String name = col.getColumnName();
                    if (name != null && !name.isEmpty()) {
                        qualifiedCols.add(alias != null ? alias + "." + name : name);
                    }
                } else if (current instanceof BinaryExpression) {
                    BinaryExpression bin = (BinaryExpression) current;
                    if (bin.getLeftExpression() != null) stack.push(bin.getLeftExpression());
                    if (bin.getRightExpression() != null) stack.push(bin.getRightExpression());
                } else if (current instanceof LikeExpression) {
                    LikeExpression like = (LikeExpression) current;
                    if (like.getLeftExpression() != null) stack.push(like.getLeftExpression());
                    if (like.getRightExpression() != null) stack.push(like.getRightExpression());
                } else if (current instanceof InExpression) {
                    InExpression in = (InExpression) current;
                    if (in.getLeftExpression() != null) stack.push(in.getLeftExpression());
                } else if (current instanceof Between) {
                    Between between = (Between) current;
                    if (between.getLeftExpression() != null) stack.push(between.getLeftExpression());
                } else if (current instanceof Parenthesis) {
                    Parenthesis paren = (Parenthesis) current;
                    if (paren.getExpression() != null) stack.push(paren.getExpression());
                } else if (current instanceof AndExpression || current instanceof OrExpression) {
                    if (current instanceof AndExpression) {
                        AndExpression and = (AndExpression) current;
                        if (and.getLeftExpression() != null) stack.push(and.getLeftExpression());
                        if (and.getRightExpression() != null) stack.push(and.getRightExpression());
                    } else {
                        OrExpression or = (OrExpression) current;
                        if (or.getLeftExpression() != null) stack.push(or.getLeftExpression());
                        if (or.getRightExpression() != null) stack.push(or.getRightExpression());
                    }
                }
                // SubSelect и Function не влияют на извлечение колонок, поэтому игнорируем
            }
        } catch (Exception e) {
            log.debug("Не удалось распарсить условие для qualified columns: {}", condition);
        }
        return qualifiedCols;
    }

    private JsonNode getChildPlan(JsonNode parent, int index) {
        JsonNode plans = parent.get("Plans");
        if (plans != null && plans.isArray() && plans.size() > index) {
            return plans.get(index);
        }
        return null;
    }

    private String findAliasInPlan(JsonNode node) {
        if (node == null) return null;
        String alias = getNodeText(node, "Alias");
        if (!alias.isEmpty()) return alias;
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                String result = findAliasInPlan(child);
                if (result != null) return result;
            }
        }
        return null;
    }

    private String findRelationNameInPlan(JsonNode node) {
        if (node == null) return null;
        String rel = getNodeText(node, "Relation Name");
        if (!rel.isEmpty()) return rel;
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                String result = findRelationNameInPlan(child);
                if (result != null) return result;
            }
        }
        return null;
    }

    private String addLimitToQuery(String originalSql, int limit) {
        try {
            String trimmed = originalSql.trim();
            boolean hasSemicolon = trimmed.endsWith(";");
            String base = hasSemicolon ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
            if (base.toUpperCase().contains("LIMIT")) return originalSql;
            String result = base + " LIMIT " + limit;
            if (hasSemicolon) result += ";";
            return result;
        } catch (Exception e) {
            return originalSql + " LIMIT " + limit;
        }
    }

    private void collectMetrics(JsonNode node, PlanMetrics metrics) {
        if (node == null) return;
        collectMetricsRecursive(node, metrics, 0);
    }

    private void collectMetricsRecursive(JsonNode node, PlanMetrics metrics, int depth) {
        if (node == null || node.isMissingNode()) return;
        metrics.nodeCount++;
        metrics.maxDepth = Math.max(metrics.maxDepth, depth);
        String nodeType = getNodeText(node, "Node Type");
        metrics.totalCost += getNodeDouble(node, "Total Cost");
        metrics.actualTotalTime += getNodeDouble(node, "Actual Total Time");
        metrics.actualRows += getNodeLong(node, "Actual Rows");
        metrics.plannedRows += getNodeLong(node, "Plan Rows");

        if ("Seq Scan".equals(nodeType)) metrics.seqScanCount++;
        else if ("Index Scan".equals(nodeType)) metrics.indexScanCount++;
        else if ("Index Only Scan".equals(nodeType)) metrics.indexOnlyScanCount++;
        else if ("Bitmap Heap Scan".equals(nodeType)) metrics.bitmapScanCount++;
        else if ("Sort".equals(nodeType)) metrics.sortCount++;
        else if ("Nested Loop".equals(nodeType)) metrics.nestedLoopCount++;
        else if ("Hash Join".equals(nodeType)) metrics.hashJoinCount++;
        else if ("Merge Join".equals(nodeType)) metrics.mergeJoinCount++;
        else if ("HashAggregate".equals(nodeType) || "GroupAggregate".equals(nodeType)) metrics.aggregateCount++;

        metrics.tempWrittenBlocks += getNodeLong(node, "Temp Written Blocks");

        if (node.has("Workers")) {
            metrics.parallelWorkerCount = Math.max(metrics.parallelWorkerCount, node.get("Workers").size());
        }

        if (node.has("JIT")) {
            metrics.jitAvailable = true;
            JsonNode jit = node.get("JIT");
            if (jit.has("Functions") && jit.get("Functions").asInt() > 0) {
                metrics.jitUsed = true;
            }
        }

        // Определение коррелированных подзапросов (упрощённо)
        if ("SubPlan".equals(nodeType) || "Subquery Scan".equals(nodeType)) {
            metrics.correlatedSubqueryCount++;
        }

        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                collectMetricsRecursive(child, metrics, depth + 1);
            }
        }
    }

    private void collectContextInfo(JsonNode node, AnalysisContext context) {
        if (node == null) return;
        String nodeType = getNodeText(node, "Node Type");
        if (nodeType.contains("Scan")) context.scanNodes.add(node);
        else if (nodeType.contains("Join")) context.joinNodes.add(node);
        else if (nodeType.contains("Subquery") || nodeType.contains("SubPlan")) context.subqueryNodes.add(node);
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) collectContextInfo(child, context);
        }
    }

    private void addRecommendation(List<Recommendation> list, RecommendationType type, String desc, ImpactLevel impact, Double improvement, String sqlCmd) {
        list.add(Recommendation.builder()
                .type(type)
                .description(desc)
                .impact(impact)
                .estimatedImprovement(improvement)
                .sqlCommand(sqlCmd)
                .sqlSuggestion(generateSqlSuggestion(type))
                .applied(false)
                .warnings(new ArrayList<>())
                .metrics(new HashMap<>())
                .build());
    }

    private String generateSqlSuggestion(RecommendationType type) {
        switch (type) {
            case CREATE_INDEX: return "Создать индекс: CREATE INDEX ...;";
            case ADD_LIMIT: return "Добавить LIMIT: SELECT ... LIMIT N;";
            case INCREASE_WORK_MEM: return "Увеличить work_mem: SET work_mem = '64MB';";
            case UPDATE_STATISTICS: return "Обновить статистику: ANALYZE;";
            case REWRITE_JOIN_TO_SEMI_JOIN: return "Переписать JOIN: замените EXISTS на INNER JOIN";
            case FIX_DATA_TYPE_MISMATCH: return "Исправить типы данных: ALTER COLUMN ... TYPE ...;";
            case ENABLE_PARALLEL_QUERY: return "Включить параллельные запросы: ALTER SYSTEM SET max_parallel_workers_per_gather = 4;";
            case SUGGEST_PARTITIONING: return "Партиционирование: CREATE TABLE ... PARTITION BY ...;";
            case ENABLE_JIT_COMPILATION: return "Включить JIT: SET jit = on;";
            case OPTIMIZE_GROUP_BY: return "Оптимизировать GROUP BY: создайте индекс или материализованное представление";
            case AVOID_FUNCTION_IN_WHERE: return "Избегать функций в WHERE: перепишите запрос или создайте функциональный индекс";
            default: return "Проверьте план выполнения запроса.";
        }
    }

    private List<Recommendation> sortAndDeduplicate(List<Recommendation> recs) {
        Map<String, Recommendation> unique = new LinkedHashMap<>();
        for (Recommendation r : recs) {
            String key = r.getSqlCommand() != null ? r.getType() + ":" + r.getSqlCommand() : r.getType() + ":" + r.getDescription();
            unique.putIfAbsent(key, r);
        }
        List<Recommendation> list = new ArrayList<>(unique.values());
        list.sort((a, b) -> {
            int wa = getImpactWeight(a.getImpact());
            int wb = getImpactWeight(b.getImpact());
            if (wa != wb) return Integer.compare(wb, wa);
            double pa = a.getEstimatedImprovement() != null ? a.getEstimatedImprovement() : 0;
            double pb = b.getEstimatedImprovement() != null ? b.getEstimatedImprovement() : 0;
            return Double.compare(pb, pa);
        });
        return list.size() > 20 ? list.subList(0, 20) : list;
    }

    private int getImpactWeight(ImpactLevel level) {
        if (level == ImpactLevel.HIGH) return 3;
        if (level == ImpactLevel.MEDIUM) return 2;
        if (level == ImpactLevel.LOW) return 1;
        return 0;
    }

    private Recommendation createErrorRecommendation(String msg) {
        return Recommendation.builder()
                .type(RecommendationType.OTHER)
                .description(msg)
                .impact(ImpactLevel.HIGH)
                .estimatedImprovement(0.0)
                .applied(false)
                .warnings(List.of("Ошибка анализа"))
                .build();
    }

    private String getNodeText(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asText() : "";
    }

    private long getNodeLong(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asLong() : 0L;
    }

    private double getNodeDouble(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asDouble() : 0.0;
    }

    private String formatNumber(long number) {
        if (number < 1000) return String.valueOf(number);
        if (number < 1_000_000) return String.format("%.1f тыс.", number / 1000.0);
        if (number < 1_000_000_000) return String.format("%.1f млн", number / 1_000_000.0);
        return String.format("%.1f млрд", number / 1_000_000_000.0);
    }
}