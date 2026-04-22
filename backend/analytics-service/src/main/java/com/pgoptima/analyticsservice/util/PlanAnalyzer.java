package com.pgoptima.analyticsservice.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.pgoptima.analyticsservice.config.PlanAnalyzerProperties;
import com.pgoptima.shareddto.enums.ImpactLevel;
import com.pgoptima.shareddto.enums.RecommendationType;
import com.pgoptima.shareddto.response.Recommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlanAnalyzer {

    private final PlanAnalyzerProperties properties;

    private static class AnalysisContext {
        String originalQuery;
        Set<String> whereColumns = new HashSet<>();
        Set<String> joinColumns = new HashSet<>();
        Set<String> orderByColumns = new HashSet<>();
        Set<String> groupByColumns = new HashSet<>();
        boolean hasLimit = false;
        boolean hasDistinct = false;
        PlanMetrics metrics = new PlanMetrics();
        List<JsonNode> scanNodes = new ArrayList<>();
        List<JsonNode> joinNodes = new ArrayList<>();
        List<JsonNode> sortNodes = new ArrayList<>();
    }

    public List<Recommendation> analyze(JsonNode plan, String originalQuery) {
        List<Recommendation> recommendations = new ArrayList<>();
        if (plan == null || plan.isMissingNode()) {
            recommendations.add(createErrorRecommendation("План выполнения отсутствует"));
            return recommendations;
        }

        AnalysisContext context = new AnalysisContext();
        context.originalQuery = originalQuery;
        collectMetrics(plan, context.metrics);
        collectContextInfo(plan, context);
        analyzeOriginalQuery(context);

        analyzeSeqScans(recommendations, context);
        analyzeMissingJoinIndexes(recommendations, context);
        analyzeSorts(recommendations, context);
        analyzeCartesianJoins(recommendations, context);
        analyzeMissingLimit(recommendations, context);
        analyzeOrConditions(recommendations, context);
        analyzeDiskTemporaryFiles(recommendations, context);
        analyzeJoinTypes(recommendations, context);
        analyzeSubqueries(recommendations, context);
        analyzeAntiJoins(recommendations, context);
        analyzeLikePattern(recommendations, context);
        analyzeDistinctWithoutIndex(recommendations, context);
        analyzeJsonbAccess(recommendations, context);
        analyzeOutdatedStats(recommendations, context);
        generateOptimizedQueries(recommendations, context);

        return sortAndDeduplicate(recommendations);
    }



    private void analyzeSeqScans(List<Recommendation> recommendations, AnalysisContext ctx) {
        if (ctx.metrics.seqScanCount == 0) return;
        for (JsonNode node : ctx.scanNodes) {
            String nodeType = getNodeText(node, "Node Type");
            if ("Seq Scan".equals(nodeType)) {
                long rows = getNodeLong(node, "Actual Rows");
                long totalRows = getNodeLong(node, "Plan Rows");
                String filter = getNodeText(node, "Filter");
                String table = getNodeText(node, "Relation Name");
                if (rows > properties.getSeqScanThreshold() && !filter.isEmpty()) {
                    List<String> cols = new ArrayList<>(ctx.whereColumns);
                    if (cols.isEmpty()) {
                        cols = extractColumnsFromFilter(filter);
                    }
                    if (!cols.isEmpty()) {

                        double estimatedImprovement = calculateIndexImprovement(rows, totalRows, filter);
                        String indexCmd = String.format("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_%s_%s ON %s(%s);",
                                table, cols.get(0).toLowerCase(), table, String.join(", ", cols));
                        addRecommendation(recommendations, RecommendationType.CREATE_INDEX,
                                String.format("Полное сканирование таблицы «%s» (обработано %d строк). Создайте индекс для ускорения фильтрации.", table, rows),
                                ImpactLevel.HIGH, estimatedImprovement, indexCmd);
                    }
                }
            }
        }
    }

    private void analyzeMissingJoinIndexes(List<Recommendation> recommendations, AnalysisContext ctx) {
        if (ctx.metrics.joinCount == 0) return;
        for (JsonNode joinNode : ctx.joinNodes) {
            long rows = getNodeLong(joinNode, "Actual Rows");
            if (rows > 10000 && !joinNode.has("Hash Cond") && !joinNode.has("Merge Cond")) {
                addRecommendation(recommendations, RecommendationType.CREATE_INDEX,
                        "Обнаружено соединение без условий (декартово произведение) или без индексов. Добавьте условия соединения и/или индексы.",
                        ImpactLevel.HIGH, 80.0, null);
            }
        }
        if (ctx.joinColumns != null && !ctx.joinColumns.isEmpty()) {
            String columns = String.join(", ", ctx.joinColumns);

            addRecommendation(recommendations, RecommendationType.CREATE_INDEX,
                    "Для ускорения JOIN-операций создайте индексы на столбцах, участвующих в соединениях: " + columns,
                    ImpactLevel.MEDIUM, 30.0,
                    String.format("CREATE INDEX idx_join_%s ON таблица(%s);", columns.toLowerCase().replace(", ", "_"), columns));
        }
    }

    private void analyzeSorts(List<Recommendation> recommendations, AnalysisContext ctx) {
        if (ctx.metrics.sortCount == 0) return;
        boolean diskSort = ctx.metrics.diskSortCount > 0;
        if (diskSort || ctx.metrics.sortCount > 0) {
            double improvement = diskSort ? 70.0 : 35.0;
            String suggestion = diskSort ?
                    "Сортировка выполняется на диске из-за нехватки памяти work_mem. Увеличьте work_mem или создайте индекс для сортировки." :
                    "Сортировка больших объёмов данных без индекса. Рассмотрите создание индекса для ORDER BY.";
            String sqlCmd = diskSort ? "SET work_mem = '64MB';" : null;
            if (!ctx.orderByColumns.isEmpty()) {
                String table = findTableForSort(ctx);
                if (table != null) {
                    sqlCmd = String.format("CREATE INDEX idx_%s_sort ON %s(%s);", table, table, String.join(", ", ctx.orderByColumns));
                }
            }
            addRecommendation(recommendations, RecommendationType.INCREASE_WORK_MEM, suggestion,
                    diskSort ? ImpactLevel.HIGH : ImpactLevel.MEDIUM, improvement, sqlCmd);
        }
    }

    private void analyzeCartesianJoins(List<Recommendation> recommendations, AnalysisContext ctx) {
        for (JsonNode joinNode : ctx.joinNodes) {
            if (!joinNode.has("Join Filter") && !joinNode.has("Hash Cond") && !joinNode.has("Merge Cond")) {
                addRecommendation(recommendations, RecommendationType.REWRITE_QUERY,
                        "Обнаружено декартово произведение (CROSS JOIN). Добавьте условие соединения для фильтрации строк.",
                        ImpactLevel.HIGH, 80.0, null);
            }
        }
    }

    private void analyzeMissingLimit(List<Recommendation> recommendations, AnalysisContext ctx) {
        if (!ctx.hasLimit && ctx.metrics.actualRows > 1000 && ctx.originalQuery != null) {
            double improvement = calculateLimitImprovement(ctx.metrics.actualRows);
            String modifiedQuery = ctx.originalQuery + " LIMIT 100";
            addRecommendation(recommendations, RecommendationType.ADD_LIMIT,
                    "Запрос возвращает большое количество строк (" + ctx.metrics.actualRows + "). Добавьте LIMIT для ограничения выборки.",
                    ImpactLevel.MEDIUM, improvement, modifiedQuery);
        }
    }

    private void analyzeOrConditions(List<Recommendation> recommendations, AnalysisContext ctx) {
        if (ctx.originalQuery != null && ctx.originalQuery.toUpperCase().contains(" OR ") && !ctx.originalQuery.toUpperCase().contains(" IN ")) {
            addRecommendation(recommendations, RecommendationType.REWRITE_QUERY,
                    "Использование OR может быть неэффективным. Замените на IN или UNION.",
                    ImpactLevel.LOW, 15.0, null);
        }
    }

    private void analyzeDiskTemporaryFiles(List<Recommendation> recommendations, AnalysisContext ctx) {
        if (ctx.metrics.tempWrittenBlocks > properties.getHighBufferThreshold()) {
            addRecommendation(recommendations, RecommendationType.INCREASE_WORK_MEM,
                    "Запрос использует временные файлы на диске (" + ctx.metrics.tempWrittenBlocks + " блоков). Увеличьте параметр work_mem.",
                    ImpactLevel.HIGH, 40.0, "SET work_mem = '128MB';");
        }
    }

    private void analyzeJoinTypes(List<Recommendation> recommendations, AnalysisContext ctx) {
        if (ctx.metrics.nestedLoopCount > 0 && ctx.metrics.nestedLoopCount > ctx.metrics.hashJoinCount) {
            addRecommendation(recommendations, RecommendationType.CHANGE_JOIN_TYPE,
                    "Преобладают вложенные циклы (Nested Loop). Для больших наборов данных эффективнее Hash Join. Проверьте статистику и наличие индексов.",
                    ImpactLevel.MEDIUM, 25.0, null);
        }
    }

    private void analyzeSubqueries(List<Recommendation> recommendations, AnalysisContext ctx) {
        if (ctx.metrics.subqueryCount > 0 && ctx.metrics.actualRows > 10000) {
            addRecommendation(recommendations, RecommendationType.REWRITE_QUERY,
                    "Обнаружен подзапрос, обрабатывающий много строк. Рассмотрите возможность переписывания через JOIN.",
                    ImpactLevel.MEDIUM, 30.0, null);
        }
    }

    private void analyzeAntiJoins(List<Recommendation> recommendations, AnalysisContext ctx) {
        if (ctx.originalQuery != null && (ctx.originalQuery.toUpperCase().contains(" NOT IN ") || ctx.originalQuery.toUpperCase().contains(" NOT EXISTS "))) {
            addRecommendation(recommendations, RecommendationType.CREATE_INDEX,
                    "Операция NOT IN или NOT EXISTS может быть медленной без индекса. Убедитесь, что на подзапросе есть индекс.",
                    ImpactLevel.MEDIUM, 35.0, null);
        }
    }

    private void analyzeLikePattern(List<Recommendation> recommendations, AnalysisContext ctx) {
        if (ctx.originalQuery != null && ctx.originalQuery.toLowerCase().contains(" like '%")) {
            addRecommendation(recommendations, RecommendationType.CREATE_INDEX,
                    "Поиск по шаблону с ведущим '%' не использует обычный B-tree индекс. Установите расширение pg_trgm и создайте GIN-индекс.",
                    ImpactLevel.MEDIUM, 50.0,
                    "CREATE EXTENSION IF NOT EXISTS pg_trgm;\nCREATE INDEX CONCURRENTLY idx_table_column_trgm ON table USING GIN(column gin_trgm_ops);");
        }
    }

    private void analyzeDistinctWithoutIndex(List<Recommendation> recommendations, AnalysisContext ctx) {
        if (ctx.hasDistinct && ctx.metrics.actualRows > 10000) {
            addRecommendation(recommendations, RecommendationType.CREATE_INDEX,
                    "Операция DISTINCT выполняется на большом количестве строк. Создайте индекс на столбцах, по которым делается DISTINCT.",
                    ImpactLevel.LOW, 20.0, null);
        }
    }

    private void analyzeJsonbAccess(List<Recommendation> recommendations, AnalysisContext ctx) {
        if (ctx.originalQuery != null && (ctx.originalQuery.contains("->") || ctx.originalQuery.contains("->>"))) {
            addRecommendation(recommendations, RecommendationType.CREATE_INDEX,
                    "Доступ к JSONB-полям без GIN-индекса приводит к полному сканированию. Создайте GIN-индекс.",
                    ImpactLevel.MEDIUM, 45.0,
                    "CREATE INDEX idx_table_jsonb ON table USING GIN(jsonb_column);");
        }
    }

    private void analyzeOutdatedStats(List<Recommendation> recommendations, AnalysisContext ctx) {
        if (ctx.metrics.actualRows > 0 && ctx.metrics.plannedRows > 0) {
            double ratio = Math.abs(ctx.metrics.actualRows - ctx.metrics.plannedRows) / (double) ctx.metrics.actualRows;
            if (ratio > 2.0) {
                double improvement = Math.min(80.0, ratio * 20.0);
                addRecommendation(recommendations, RecommendationType.UPDATE_STATISTICS,
                        "Статистика оптимизатора устарела (оценка строк отличается от реальной более чем в 2 раза). Выполните ANALYZE.",
                        ImpactLevel.HIGH, improvement, "ANALYZE;");
            }
        }
    }

    private void generateOptimizedQueries(List<Recommendation> recommendations, AnalysisContext ctx) {
        if (!ctx.hasLimit && ctx.metrics.actualRows > 500) {
            double improvement = calculateLimitImprovement(ctx.metrics.actualRows);
            String modifiedQuery = ctx.originalQuery + " LIMIT 100";
            addRecommendation(recommendations, RecommendationType.ADD_LIMIT,
                    "Запрос возвращает много строк. Добавьте LIMIT для снижения нагрузки.",
                    ImpactLevel.LOW, improvement, modifiedQuery);
        }
    }



    private double calculateIndexImprovement(long actualRows, long totalRows, String filter) {

        if (totalRows <= 0) totalRows = actualRows;
        if (actualRows == 0) return 50.0;


        double selectivity = (double) actualRows / totalRows;

        if (selectivity >= 0.3) return 30.0;

        double improvement = (1.0 - selectivity) * 100.0;
        return Math.min(95.0, Math.max(20.0, improvement));
    }

    private double calculateLimitImprovement(long actualRows) {
        if (actualRows <= 0) return 0;
        long limitRows = 100;
        if (actualRows <= limitRows) return 0;
        double reduction = 1.0 - (double) limitRows / actualRows;

        return 10;
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

        switch (nodeType) {
            case "Seq Scan":
                metrics.seqScanCount++;
                long rows = getNodeLong(node, "Actual Rows");
                if (rows > metrics.maxSeqScanRows) {
                    metrics.maxSeqScanRows = rows;
                    metrics.largestSeqScanTable = getNodeText(node, "Relation Name");
                }
                break;
            case "Index Scan":
            case "Index Only Scan":
                metrics.indexScanCount++;
                break;
            case "Nested Loop":
                metrics.nestedLoopCount++;
                metrics.joinCount++;
                break;
            case "Hash Join":
                metrics.hashJoinCount++;
                metrics.joinCount++;
                break;
            case "Merge Join":
                metrics.mergeJoinCount++;
                metrics.joinCount++;
                break;
            case "Sort":
                metrics.sortCount++;
                if ("Disk".equals(getNodeText(node, "Sort Space Type"))) {
                    metrics.diskSortCount++;
                    metrics.diskSortRows += getNodeLong(node, "Actual Rows");
                } else {
                    metrics.memorySortCount++;
                }
                break;
            case "Aggregate":
            case "HashAggregate":
                metrics.aggregateCount++;
                break;
            case "Subquery Scan":
                metrics.subqueryCount++;
                break;
        }

        metrics.sharedHitBlocks += getNodeLong(node, "Shared Hit Blocks");
        metrics.sharedReadBlocks += getNodeLong(node, "Shared Read Blocks");
        metrics.tempReadBlocks += getNodeLong(node, "Temp Read Blocks");
        metrics.tempWrittenBlocks += getNodeLong(node, "Temp Written Blocks");

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
        else if ("Sort".equals(nodeType)) context.sortNodes.add(node);

        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) collectContextInfo(child, context);
        }
    }

    private void analyzeOriginalQuery(AnalysisContext context) {
        if (context.originalQuery == null) return;
        String upper = context.originalQuery.toUpperCase();
        context.hasLimit = upper.contains("LIMIT");
        context.hasDistinct = upper.contains("DISTINCT");

        try {
            net.sf.jsqlparser.statement.Statement stmt = CCJSqlParserUtil.parse(context.originalQuery);
            if (stmt instanceof Select) {
                PlainSelect select = (PlainSelect) ((Select) stmt).getSelectBody();
                if (select.getWhere() != null) {
                    extractColumns(select.getWhere(), context.whereColumns);
                }
                if (select.getJoins() != null) {
                    select.getJoins().forEach(join -> {
                        if (join.getOnExpression() != null) extractColumns(join.getOnExpression(), context.joinColumns);
                    });
                }
                if (select.getOrderByElements() != null) {
                    select.getOrderByElements().forEach(ob -> extractColumns(ob.getExpression(), context.orderByColumns));
                }
                if (select.getGroupBy() != null && select.getGroupBy().getGroupByExpressions() != null) {
                    select.getGroupBy().getGroupByExpressions().forEach(expr -> extractColumns(expr, context.groupByColumns));
                }
            }
        } catch (JSQLParserException e) {
            log.debug("JSqlParser не смог разобрать запрос, используются эвристики");
        }
    }

    private void extractColumns(Expression expr, Set<String> target) {
        if (expr == null) return;
        expr.accept(new net.sf.jsqlparser.expression.ExpressionVisitorAdapter() {
            @Override
            public void visit(net.sf.jsqlparser.schema.Column column) {
                target.add(column.getColumnName());
            }
        });
    }

    private List<String> extractColumnsFromFilter(String filter) {
        List<String> cols = new ArrayList<>();
        Pattern p = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?:=|>|<|>=|<=|LIKE|IN)");
        Matcher m = p.matcher(filter);
        while (m.find()) {
            cols.add(m.group(1));
        }
        return cols;
    }

    private String findTableForSort(AnalysisContext ctx) {
        if (!ctx.scanNodes.isEmpty()) {
            return getNodeText(ctx.scanNodes.get(0), "Relation Name");
        }
        return null;
    }

    private void addRecommendation(List<Recommendation> list, RecommendationType type,
                                   String desc, ImpactLevel impact, Double improvement, String sqlCmd) {
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
        return switch (type) {
            case CREATE_INDEX -> "Создать индекс: CREATE INDEX ...;";
            case ADD_LIMIT -> "Добавить LIMIT: SELECT ... LIMIT N;";
            case INCREASE_WORK_MEM -> "Увеличить work_mem: SET work_mem = '64MB';";
            case UPDATE_STATISTICS -> "Обновить статистику: ANALYZE;";
            case CHANGE_JOIN_TYPE -> "Изменить тип соединения (например, использовать Hash Join)";
            default -> "Проверьте план выполнения запроса.";
        };
    }

    private List<Recommendation> sortAndDeduplicate(List<Recommendation> recs) {
        Map<String, Recommendation> unique = new LinkedHashMap<>();
        for (Recommendation r : recs) {
            String key = r.getSqlCommand() != null ? r.getType() + ":" + r.getSqlCommand() : r.getType() + ":" + r.getDescription();
            unique.putIfAbsent(key, r);
        }
        List<Recommendation> list = new ArrayList<>(unique.values());
        list.sort((a, b) -> {
            double pa = getImpactWeight(a.getImpact()) * (a.getEstimatedImprovement() != null ? a.getEstimatedImprovement() : 0);
            double pb = getImpactWeight(b.getImpact()) * (b.getEstimatedImprovement() != null ? b.getEstimatedImprovement() : 0);
            return Double.compare(pb, pa);
        });
        return list.size() > 20 ? list.subList(0, 20) : list;
    }

    private double getImpactWeight(ImpactLevel level) {
        return switch (level) {
            case HIGH -> 3.0;
            case MEDIUM -> 2.0;
            default -> 1.0;
        };
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
}