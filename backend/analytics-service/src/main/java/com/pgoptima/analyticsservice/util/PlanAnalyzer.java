package com.pgoptima.analyticsservice.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgoptima.shareddto.enums.ImpactLevel;
import com.pgoptima.shareddto.enums.RecommendationType;
import com.pgoptima.shareddto.response.Recommendation;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.util.*;
import java.util.stream.Collectors;

public class PlanAnalyzer {

    // Константы для пороговых значений анализа
    private static final long SEQ_SCAN_THRESHOLD = 1000;
    private static final double HIGH_COST_RATIO = 0.3;
    private static final long HIGH_BUFFER_THRESHOLD = 10000;
    private static final double PARALLEL_EFFECTIVENESS_THRESHOLD = 1.5;
    private static final long INDEX_SCAN_ROW_THRESHOLD = 10000;
    private static final double CACHE_HIT_RATIO_THRESHOLD = 0.8;
    private static final long LARGE_JOIN_THRESHOLD = 100000;
    private static final long HIGH_EXECUTION_TIME_MS = 5000;
    private static final int MAX_JOIN_COUNT = 5;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Структура для хранения контекста анализа
    private static class AnalysisContext {
        String originalQuery;
        Map<String, String> tableAliases = new HashMap<>();
        Map<String, Set<String>> tableColumns = new HashMap<>();
        Set<String> existingIndexes = new HashSet<>();
        List<JsonNode> scanNodes = new ArrayList<>();
        List<JsonNode> joinNodes = new ArrayList<>();
        List<JsonNode> sortNodes = new ArrayList<>();
        PlanMetrics metrics;
        Set<String> allColumnsInQuery = new HashSet<>();
        Map<String, Set<String>> tableUsedColumns = new HashMap<>();
        Set<String> joinConditions = new HashSet<>();
        Set<String> whereConditions = new HashSet<>();
        Set<String> orderByColumns = new HashSet<>();
        Set<String> groupByColumns = new HashSet<>();
    }

    /**
     * Основной метод анализа плана выполнения с генерацией конкретных SQL команд.
     */
    public static List<Recommendation> analyze(JsonNode plan, String originalQuery) {
        List<Recommendation> recommendations = new ArrayList<>();

        if (plan == null || plan.isMissingNode()) {
            recommendations.add(createErrorRecommendation("План выполнения отсутствует или пуст"));
            return recommendations;
        }

        try {
            // Создаем контекст анализа
            AnalysisContext context = new AnalysisContext();
            context.originalQuery = originalQuery;
            context.metrics = collectMetrics(plan);

            // Собираем дополнительную информацию из плана
            collectContextInfo(plan, context);

            // Анализируем исходный SQL запрос для извлечения колонок и условий
            analyzeOriginalQuery(context);

            // 1. Анализ сканирования таблиц с генерацией индексов
            analyzeScans(plan, recommendations, context);

            // 2. Анализ операций соединения
            analyzeJoins(plan, recommendations, context);

            // 3. Анализ операций агрегации
            analyzeAggregations(plan, recommendations, context);

            // 4. Анализ операций сортировки
            analyzeSortOperations(plan, recommendations, context);

            // 5. Анализ использования памяти и буферов
            analyzeMemoryUsage(plan, recommendations, context);

            // 6. Анализ параллельного выполнения
            analyzeParallelism(plan, recommendations, context);

            // 7. Анализ вложенных запросов
            analyzeSubqueries(plan, recommendations, context);

            // 8. Анализ временных таблиц
            analyzeTempTables(plan, recommendations, context);

            // 9. Анализ индексов
            analyzeIndexUsage(plan, recommendations, context);

            // 10. Генерация оптимизированных версий запроса
            generateOptimizedQueries(recommendations, context);

            // 11. Общий анализ производительности
            analyzeOverallPerformance(plan, recommendations, context);

            // 12. Анализ статистики
            analyzeStatistics(plan, recommendations, context);

            // 13. Анализ рекурсивных операций
            analyzeRecursiveOperations(plan, recommendations, context);

            // 14. Анализ оконных функций
            analyzeWindowFunctions(plan, recommendations, context);

            // 15. Анализ блокировок
            analyzeLockingBehavior(plan, recommendations, context);

            // Сортировка рекомендаций по приоритету
            return sortAndDeduplicateRecommendations(recommendations, context.metrics);

        } catch (Exception e) {
            recommendations.add(createErrorRecommendation("Ошибка анализа плана: " + e.getMessage()));
            return recommendations;
        }
    }

    /**
     * Анализ исходного SQL запроса для извлечения колонок и условий
     */
    private static void analyzeOriginalQuery(AnalysisContext context) {
        if (context.originalQuery == null || context.originalQuery.trim().isEmpty()) {
            return;
        }

        try {
            Statement statement = CCJSqlParserUtil.parse(context.originalQuery);
            if (statement instanceof Select) {
                Select selectStatement = (Select) statement;
                SelectBody selectBody = selectStatement.getSelectBody();

                if (selectBody instanceof PlainSelect) {
                    PlainSelect plainSelect = (PlainSelect) selectBody;

                    // Извлекаем имена таблиц
                    TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
                    List<String> tableList = tablesNamesFinder.getTableList(statement);

                    // Извлекаем колонки из SELECT
                    extractColumnsFromSelectItems(plainSelect.getSelectItems(), context);

                    // Извлекаем условия WHERE
                    extractColumnsFromExpression(plainSelect.getWhere(), context, "WHERE");

                    // Извлекаем условия JOIN
                    if (plainSelect.getJoins() != null) {
                        for (Join join : plainSelect.getJoins()) {
                            extractColumnsFromExpression(join.getOnExpression(), context, "JOIN");
                        }
                    }

                    // Извлекаем ORDER BY
                    if (plainSelect.getOrderByElements() != null) {
                        for (OrderByElement orderBy : plainSelect.getOrderByElements()) {
                            extractColumnsFromExpression(orderBy.getExpression(), context, "ORDER BY");
                        }
                    }

                    // Извлекаем GROUP BY
                    if (plainSelect.getGroupBy() != null && plainSelect.getGroupBy().getGroupByExpressions() != null) {
                        for (Expression expr : plainSelect.getGroupBy().getGroupByExpressions()) {
                            extractColumnsFromExpression(expr, context, "GROUP BY");
                        }
                    }

                    // Извлекаем HAVING
                    extractColumnsFromExpression(plainSelect.getHaving(), context, "HAVING");
                }
            }
        } catch (JSQLParserException e) {
            // Если не удалось распарсить с помощью JSqlParser, используем простую эвристику
            extractColumnsWithHeuristics(context);
        }
    }

    /**
     * Извлечение колонок из элементов SELECT с помощью JSqlParser
     */
    private static void extractColumnsFromSelectItems(List<SelectItem> selectItems, AnalysisContext context) {
        if (selectItems == null) return;

        for (SelectItem item : selectItems) {
            if (item instanceof SelectExpressionItem) {
                SelectExpressionItem exprItem = (SelectExpressionItem) item;
                extractColumnsFromExpression(exprItem.getExpression(), context, "SELECT");
            }
        }
    }

    /**
     * Извлечение колонок из выражения с помощью JSqlParser
     */
    private static void extractColumnsFromExpression(Expression expression, AnalysisContext context, String contextType) {
        if (expression == null) return;

        expression.accept(new ExpressionVisitorAdapter() {
            @Override
            public void visit(Column column) {
                String columnName = column.getColumnName();
                String tableName = column.getTable() != null ? column.getTable().getName() : null;

                context.allColumnsInQuery.add(columnName);

                if (tableName != null) {
                    context.tableUsedColumns.computeIfAbsent(tableName, k -> new HashSet<>()).add(columnName);

                    // Сохраняем контекст использования
                    switch (contextType) {
                        case "WHERE":
                            context.whereConditions.add(columnName);
                            break;
                        case "JOIN":
                            context.joinConditions.add(columnName);
                            break;
                        case "ORDER BY":
                            context.orderByColumns.add(columnName);
                            break;
                        case "GROUP BY":
                            context.groupByColumns.add(columnName);
                            break;
                    }
                }
            }

            public void visit(BinaryExpression expr) {
                // Обрабатываем левую и правую части бинарных выражений
                expr.getLeftExpression().accept(this);
                expr.getRightExpression().accept(this);
            }

            @Override
            public void visit(Parenthesis parenthesis) {
                parenthesis.getExpression().accept(this);
            }

            @Override
            public void visit(AndExpression expr) {
                expr.getLeftExpression().accept(this);
                expr.getRightExpression().accept(this);
            }

            @Override
            public void visit(OrExpression expr) {
                expr.getLeftExpression().accept(this);
                expr.getRightExpression().accept(this);
            }
        });
    }

    /**
     * Извлечение колонок с помощью эвристик (запасной метод)
     */
    private static void extractColumnsWithHeuristics(AnalysisContext context) {
        // Простая эвристика для извлечения колонок
        String query = context.originalQuery.toUpperCase();

        // Удаляем строковые литералы
        query = query.replaceAll("'[^']*'", "");

        // Ищем паттерны колонок (table.column или просто column)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\\b([A-Z_][A-Z0-9_]*\\.)?([A-Z_][A-Z0-9_]*)\\b",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );

        java.util.regex.Matcher matcher = pattern.matcher(context.originalQuery);
        while (matcher.find()) {
            String fullColumn = matcher.group();
            String columnName = matcher.group(2);

            if (isValidSimpleColumnName(columnName)) {
                context.allColumnsInQuery.add(columnName);
            }
        }
    }

    /**
     * Сбор метрик плана выполнения
     */
    private static PlanMetrics collectMetrics(JsonNode node) {
        PlanMetrics metrics = new PlanMetrics();
        if (node != null && !node.isMissingNode()) {
            collectMetricsRecursive(node, metrics, 0);
        }
        return metrics;
    }

    private static void collectMetricsRecursive(JsonNode node, PlanMetrics metrics, int depth) {
        if (node == null || node.isMissingNode()) return;

        metrics.nodeCount++;
        metrics.maxDepth = Math.max(metrics.maxDepth, depth);

        String nodeType = getNodeText(node, "Node Type");
        String relationName = getNodeText(node, "Relation Name");

        // Базовые метрики
        metrics.totalCost += getNodeDouble(node, "Total Cost");
        metrics.actualTotalTime += getNodeDouble(node, "Actual Total Time");
        metrics.actualRows += getNodeLong(node, "Actual Rows");
        metrics.plannedRows += getNodeLong(node, "Plan Rows");
        metrics.startupCost += getNodeDouble(node, "Startup Cost");
        metrics.actualLoops += getNodeLong(node, "Actual Loops");

        // Анализ по типам узлов
        switch (nodeType) {
            case "Seq Scan":
                metrics.seqScanCount++;
                long seqRows = getNodeLong(node, "Actual Rows");
                if (seqRows > metrics.maxSeqScanRows) {
                    metrics.maxSeqScanRows = seqRows;
                    metrics.largestSeqScanTable = relationName;
                }
                break;

            case "Index Scan":
            case "Index Only Scan":
                metrics.indexScanCount++;
                String indexName = getNodeText(node, "Index Name");
                if (!indexName.isEmpty()) {
                    metrics.usedIndexes.add(indexName);
                }
                if ("Index Only Scan".equals(nodeType)) {
                    metrics.indexOnlyScanCount++;
                }
                break;

            case "Bitmap Heap Scan":
                metrics.bitmapScanCount++;
                break;

            case "Nested Loop":
            case "Hash Join":
            case "Merge Join":
                metrics.joinCount++;
                long joinRows = getNodeLong(node, "Actual Rows");
                if (joinRows > metrics.maxJoinRows) {
                    metrics.maxJoinRows = joinRows;
                }
                if ("Nested Loop".equals(nodeType)) {
                    metrics.nestedLoopCount++;
                } else if ("Hash Join".equals(nodeType)) {
                    metrics.hashJoinCount++;
                } else if ("Merge Join".equals(nodeType)) {
                    metrics.mergeJoinCount++;
                }
                break;

            case "Sort":
                metrics.sortCount++;
                String sortSpace = getNodeText(node, "Sort Space Type");
                long sortRows = getNodeLong(node, "Actual Rows");
                if ("Disk".equals(sortSpace)) {
                    metrics.diskSortCount++;
                    metrics.diskSortRows += sortRows;
                } else {
                    metrics.memorySortCount++;
                }

                // Собираем ключи сортировки
                if (node.has("Sort Key")) {
                    String sortKey = getNodeText(node, "Sort Key");
                    if (!sortKey.isEmpty()) {
                        metrics.sortKeyColumns.add(sortKey);
                    }
                }
                break;

            case "Aggregate":
            case "GroupAggregate":
            case "HashAggregate":
                metrics.aggregateCount++;
                break;

            case "WindowAgg":
                metrics.windowFunctionCount++;
                break;

            case "CTE Scan":
            case "WorkTable Scan":
                metrics.cteScanCount++;
                break;

            case "Subquery Scan":
                metrics.subqueryCount++;
                break;

            case "Materialize":
                metrics.materializeCount++;
                break;

            case "Hash":
                metrics.hashCount++;
                break;

            case "Limit":
                metrics.limitCount++;
                break;

            case "Unique":
                metrics.uniqueCount++;
                break;

            case "SetOp":
                metrics.setOpCount++;
                break;

            case "Append":
                metrics.appendCount++;
                break;

            case "Recursive Union":
                metrics.recursiveUnionCount++;
                break;
        }

        // Анализ использования буферов
        metrics.sharedHitBlocks += getNodeLong(node, "Shared Hit Blocks");
        metrics.sharedReadBlocks += getNodeLong(node, "Shared Read Blocks");
        metrics.sharedDirtiedBlocks += getNodeLong(node, "Shared Dirtied Blocks");
        metrics.sharedWrittenBlocks += getNodeLong(node, "Shared Written Blocks");
        metrics.localHitBlocks += getNodeLong(node, "Local Hit Blocks");
        metrics.localReadBlocks += getNodeLong(node, "Local Read Blocks");
        metrics.localDirtiedBlocks += getNodeLong(node, "Local DirtiedBlocks");
        metrics.localWrittenBlocks += getNodeLong(node, "Local Written Blocks");
        metrics.tempReadBlocks += getNodeLong(node, "Temp Read Blocks");
        metrics.tempWrittenBlocks += getNodeLong(node, "Temp Written Blocks");

        // Анализ фильтров и условий
        if (node.has("Filter")) {
            metrics.filterCount++;
            String filter = getNodeText(node, "Filter");
            if (filter.contains("IS NULL") || filter.contains("IS NOT NULL")) {
                metrics.nullFilterCount++;
            }
        }

        if (node.has("Join Filter")) {
            metrics.joinFilterCount++;
        }

        if (node.has("Index Cond")) {
            metrics.indexConditionCount++;
        }

        // Рекурсивный обход дочерних узлов
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                collectMetricsRecursive(child, metrics, depth + 1);
            }
        }
    }

    /**
     * Сбор контекстной информации из плана
     */
    private static void collectContextInfo(JsonNode node, AnalysisContext context) {
        if (node == null || node.isMissingNode()) return;

        String nodeType = getNodeText(node, "Node Type");
        String relationName = getNodeText(node, "Relation Name");
        String alias = getNodeText(node, "Alias");

        // Собираем информацию о таблицах и алиасах
        if (!relationName.isEmpty()) {
            if (!alias.isEmpty()) {
                context.tableAliases.put(alias, relationName);
            }

            // Инициализируем набор колонок для таблицы
            context.tableColumns.putIfAbsent(relationName, new HashSet<>());
        }

        // Собираем узлы по типам для дальнейшего анализа
        switch (nodeType) {
            case "Seq Scan":
            case "Index Scan":
            case "Index Only Scan":
            case "Bitmap Heap Scan":
                context.scanNodes.add(node);
                break;
            case "Nested Loop":
            case "Hash Join":
            case "Merge Join":
                context.joinNodes.add(node);
                break;
            case "Sort":
                context.sortNodes.add(node);
                break;
        }

        // Собираем информацию о колонках из условий
        collectColumnInfoFromConditions(node, context);

        // Рекурсивный обход
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                collectContextInfo(child, context);
            }
        }
    }

    /**
     * Сбор информации о колонках из условий фильтрации
     */
    private static void collectColumnInfoFromConditions(JsonNode node, AnalysisContext context) {
        // Из фильтров
        if (node.has("Filter")) {
            String filter = getNodeText(node, "Filter");
            List<String> columns = extractColumnsFromConditionWithJSqlParser(filter);
            columns.forEach(col -> context.allColumnsInQuery.add(col));
        }

        // Из условий индекса
        if (node.has("Index Cond")) {
            String indexCond = getNodeText(node, "Index Cond");
            List<String> columns = extractColumnsFromConditionWithJSqlParser(indexCond);
            columns.forEach(col -> context.allColumnsInQuery.add(col));
        }

        // Из условий соединения
        if (node.has("Join Filter")) {
            String joinFilter = getNodeText(node, "Join Filter");
            List<String> columns = extractColumnsFromConditionWithJSqlParser(joinFilter);
            columns.forEach(col -> {
                context.allColumnsInQuery.add(col);
                context.joinConditions.add(col);
            });
        }

        if (node.has("Hash Cond")) {
            String hashCond = getNodeText(node, "Hash Cond");
            List<String> columns = extractColumnsFromConditionWithJSqlParser(hashCond);
            columns.forEach(col -> {
                context.allColumnsInQuery.add(col);
                context.joinConditions.add(col);
            });
        }

        if (node.has("Merge Cond")) {
            String mergeCond = getNodeText(node, "Merge Cond");
            List<String> columns = extractColumnsFromConditionWithJSqlParser(mergeCond);
            columns.forEach(col -> {
                context.allColumnsInQuery.add(col);
                context.joinConditions.add(col);
            });
        }

        // Из ключей сортировки
        if (node.has("Sort Key")) {
            String sortKey = getNodeText(node, "Sort Key");
            List<String> columns = extractColumnsFromSortKey(sortKey);
            columns.forEach(col -> {
                context.allColumnsInQuery.add(col);
                context.orderByColumns.add(col);
            });
        }

        // Из группировки
        if (node.has("Group Key")) {
            JsonNode groupKey = node.get("Group Key");
            if (groupKey.isArray()) {
                for (JsonNode key : groupKey) {
                    String keyStr = key.asText();
                    List<String> columns = extractColumnsFromExpressionHeuristic(keyStr);
                    columns.forEach(col -> {
                        context.allColumnsInQuery.add(col);
                        context.groupByColumns.add(col);
                    });
                }
            }
        }
    }

    /**
     * Извлечение колонок из условия с использованием JSqlParser
     */
    private static List<String> extractColumnsFromConditionWithJSqlParser(String condition) {
        List<String> columns = new ArrayList<>();
        if (condition == null || condition.trim().isEmpty()) {
            return columns;
        }

        try {
            // Пытаемся распарсить условие как выражение
            Expression expression = CCJSqlParserUtil.parseCondExpression(condition);
            columns = extractColumnsFromExpression(expression);
        } catch (JSQLParserException e) {
            // Если JSqlParser не смог распарсить, используем эвристику
            columns = extractColumnsFromConditionHeuristic(condition);
        }

        return columns.stream()
                .filter(col -> isValidSimpleColumnName(col))
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Извлечение колонок из выражения с помощью JSqlParser
     */
    private static List<String> extractColumnsFromExpression(Expression expression) {
        List<String> columns = new ArrayList<>();

        if (expression == null) return columns;

        expression.accept(new ExpressionVisitorAdapter() {
            @Override
            public void visit(Column column) {
                columns.add(column.getColumnName());
            }

            public void visit(BinaryExpression expr) {
                expr.getLeftExpression().accept(this);
                expr.getRightExpression().accept(this);
            }

            @Override
            public void visit(Parenthesis parenthesis) {
                parenthesis.getExpression().accept(this);
            }

            @Override
            public void visit(AndExpression expr) {
                expr.getLeftExpression().accept(this);
                expr.getRightExpression().accept(this);
            }

            @Override
            public void visit(OrExpression expr) {
                expr.getLeftExpression().accept(this);
                expr.getRightExpression().accept(this);
            }
        });

        return columns;
    }

    /**
     * Извлечение колонок из условия с помощью эвристик
     */
    private static List<String> extractColumnsFromConditionHeuristic(String condition) {
        List<String> columns = new ArrayList<>();
        if (condition == null || condition.trim().isEmpty()) {
            return columns;
        }

        // Удаляем CAST выражения (::type) и литералы
        String cleaned = condition
                .replaceAll("::[a-zA-Z_][a-zA-Z0-9_\\s]*", "")  // Удаляем ::timestamp without time zone и т.д.
                .replaceAll("'[^']*'", "")      // Удаляем строки в одинарных кавычках
                .replaceAll("\"[^\"]*\"", "")   // Удаляем строки в двойных кавычках
                .replaceAll("\\d+(\\.\\d+)?", "") // Удаляем числа
                .replaceAll("\\s+", " ")        // Удаляем лишние пробелы
                .trim();

        // Регулярное выражение для поиска имен колонок
        // Ищем слова, которые могут быть именами колонок
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b"
        );

        java.util.regex.Matcher matcher = pattern.matcher(cleaned);
        while (matcher.find()) {
            String potentialColumn = matcher.group(1);
            if (isValidSimpleColumnName(potentialColumn)) {
                columns.add(potentialColumn);
            }
        }

        return columns;
    }

    /**
     * Извлечение колонок из ключей сортировки
     */
    private static List<String> extractColumnsFromSortKey(String sortKey) {
        List<String> columns = new ArrayList<>();
        if (sortKey == null || sortKey.isEmpty()) return columns;

        // Удаляем направления сортировки (ASC/DESC)
        String cleanKey = sortKey.replaceAll("(?i)\\s+(ASC|DESC)", "");

        // Разделяем по запятым и обрабатываем каждую часть
        String[] parts = cleanKey.split("\\s*,\\s*");
        for (String part : parts) {
            part = part.trim();
            // Удаляем возможные функции
            if (part.contains("(") && part.contains(")")) {
                // Пытаемся извлечь колонку из функции
                part = part.replaceAll(".*\\((.*)\\).*", "$1");
            }

            // Проверяем, является ли валидным именем колонки
            if (isValidSimpleColumnName(part)) {
                columns.add(part);
            }
        }

        return columns;
    }

    /**
     * Извлечение колонок из выражения с помощью эвристики
     */
    private static List<String> extractColumnsFromExpressionHeuristic(String expression) {
        List<String> columns = new ArrayList<>();
        if (expression == null || expression.isEmpty()) return columns;

        // Удаляем CAST выражения и функции
        String cleaned = expression
                .replaceAll("::[a-zA-Z_][a-zA-Z0-9_\\s]*", "")
                .replaceAll("[a-zA-Z_][a-zA-Z0-9_]*\\([^)]*\\)", "") // Удаляем функции
                .replaceAll("'[^']*'", "")
                .replaceAll("\\d+(\\.\\d+)?", "")
                .replaceAll("\\s+", " ")
                .trim();

        // Ищем простые имена колонок
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b"
        );

        java.util.regex.Matcher matcher = pattern.matcher(cleaned);
        while (matcher.find()) {
            String potentialColumn = matcher.group(1);
            if (isValidSimpleColumnName(potentialColumn)) {
                columns.add(potentialColumn);
            }
        }

        return columns;
    }

    /**
     * Проверка, является ли строка валидным именем колонки
     */
    private static boolean isValidSimpleColumnName(String str) {
        if (str == null || str.isEmpty()) return false;

        // Полный список ключевых слов PostgreSQL
        Set<String> keywords = new HashSet<>(Arrays.asList(
                // SQL ключевые слова
                "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "NULL",
                "TRUE", "FALSE", "COUNT", "SUM", "AVG", "MIN", "MAX",
                "GROUP", "ORDER", "BY", "HAVING", "LIMIT", "OFFSET",
                "JOIN", "INNER", "OUTER", "LEFT", "RIGHT", "FULL",
                "ON", "AS", "IS", "IN", "BETWEEN", "LIKE", "ILIKE",
                "DISTINCT", "ALL", "ANY", "SOME", "EXISTS", "CASE",
                "WHEN", "THEN", "ELSE", "END",

                // Типы данных PostgreSQL
                "WITHOUT", "TIME", "ZONE", "TIMESTAMP", "DATE", "INTERVAL",
                "INTEGER", "BIGINT", "SMALLINT", "NUMERIC", "DECIMAL",
                "REAL", "DOUBLE", "PRECISION", "CHAR", "VARCHAR", "TEXT",
                "BYTEA", "BOOLEAN", "SERIAL", "BIGSERIAL", "MONEY",

                // Функции и операторы
                "NOW", "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP",
                "EXTRACT", "DATE_PART", "DATE_TRUNC", "COALESCE", "NULLIF",
                "GREATEST", "LEAST", "CAST", "CONVERT",

                // Прочие
                "ASC", "DESC", "NULLS", "FIRST", "LAST", "USING",
                "NATURAL", "CROSS", "UNION", "INTERSECT", "EXCEPT",
                "VALUES", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP",
                "ALTER", "TABLE", "INDEX", "VIEW", "SEQUENCE", "TRIGGER",
                "FUNCTION", "PROCEDURE", "SCHEMA", "DATABASE"
        ));

        String upper = str.toUpperCase();

        // Не должно быть ключевым словом
        if (keywords.contains(upper)) {
            return false;
        }

        // Не должно быть числом
        if (str.matches("\\d+(\\.\\d+)?")) {
            return false;
        }

        // Должно соответствовать правилам именования
        return str.matches("[a-zA-Z_][a-zA-Z0-9_]*");
    }

    /**
     * Анализ сканирования с генерацией конкретных индексов
     */
    private static void analyzeScans(JsonNode node, List<Recommendation> recommendations, AnalysisContext context) {
        if (node == null || node.isMissingNode()) return;

        String nodeType = getNodeText(node, "Node Type");

        if ("Seq Scan".equals(nodeType)) {
            long actualRows = getNodeLong(node, "Actual Rows");
            long rowsRemoved = getNodeLong(node, "Rows Removed by Filter");
            String tableName = getNodeText(node, "Relation Name");
            String alias = getNodeText(node, "Alias");
            String tableLabel = !alias.isEmpty() ? alias : tableName;
            String filter = getNodeText(node, "Filter");

            // Проверка Seq Scan без фильтров - НЕ предлагаем CREATE_INDEX!
            if (filter.isEmpty()) {
                String recommendationDesc = String.format(
                        "Полное сканирование таблицы '%s' без фильтров (%,d строк). " +
                                "Рассмотрите добавление WHERE условия или использование LIMIT.",
                        tableLabel, actualRows
                );

                // НЕ создаем рекомендацию CREATE_INDEX для Seq Scan без фильтров
                addRecommendation(
                        recommendations,
                        RecommendationType.REWRITE_QUERY,
                        recommendationDesc,
                        actualRows > SEQ_SCAN_THRESHOLD ? ImpactLevel.HIGH : ImpactLevel.MEDIUM,
                        Math.min(actualRows / 1000.0, 60.0),
                        null  // Нет SQL команды для индекса
                );
            } else {
                // Проверка Seq Scan с большим количеством строк
                double filterRatio = actualRows > 0 ? (double) rowsRemoved / actualRows : 0;

                if (actualRows > SEQ_SCAN_THRESHOLD && filterRatio > 0.1) {
                    List<String> filterColumns = extractColumnsFromConditionWithJSqlParser(filter);

                    if (!filterColumns.isEmpty()) {
                        String recommendationDesc = String.format(
                                "Seq Scan на '%s': %,d строк, отфильтровано %,d (%.1f%%). " +
                                        "Рассмотрите создание индекса на условиях фильтрации.",
                                tableLabel, actualRows, rowsRemoved, filterRatio * 100
                        );

                        // Получаем колонки из WHERE условий для этой таблицы
                        List<String> whereColumnsForTable = getWhereColumnsForTable(tableName, context);

                        if (!whereColumnsForTable.isEmpty()) {
                            addRecommendationWithIndex(
                                    recommendations,
                                    RecommendationType.CREATE_INDEX,
                                    recommendationDesc,
                                    filterRatio > 0.3 ? ImpactLevel.HIGH : ImpactLevel.MEDIUM,
                                    Math.min(filterRatio * 100, 50.0),
                                    tableName,
                                    whereColumnsForTable,
                                    "FILTERED_SCAN",
                                    context
                            );
                        }
                    }
                }
            }
        }

        // Проверка Index Scan с низкой селективностью
        if (nodeType.contains("Index Scan")) {
            long actualRows = getNodeLong(node, "Actual Rows");
            String indexName = getNodeText(node, "Index Name");
            String indexCond = getNodeText(node, "Index Cond");

            if (actualRows > INDEX_SCAN_ROW_THRESHOLD && !indexCond.isEmpty()) {
                List<String> indexColumns = extractColumnsFromConditionWithJSqlParser(indexCond);

                if (!indexColumns.isEmpty()) {
                    String recommendationDesc = String.format(
                            "Index Scan '%s' возвращает много строк (%,d). " +
                                    "Рассмотрите создание более селективного индекса.",
                            indexName, actualRows
                    );

                    addRecommendationWithIndex(
                            recommendations,
                            RecommendationType.CREATE_INDEX,
                            recommendationDesc,
                            actualRows > 100000 ? ImpactLevel.HIGH : ImpactLevel.MEDIUM,
                            25.0,
                            getNodeText(node, "Relation Name"),
                            indexColumns,
                            "LOW_SELECTIVITY_INDEX",
                            context
                    );
                }
            }
        }

        // Рекурсивный обход
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                analyzeScans(child, recommendations, context);
            }
        }
    }

    /**
     * Получение колонок из WHERE условий для конкретной таблицы
     */
    private static List<String> getWhereColumnsForTable(String tableName, AnalysisContext context) {
        List<String> columns = new ArrayList<>();

        // Собираем все колонки, которые используются в WHERE условиях
        // и относятся к этой таблице (или могут относиться)
        columns.addAll(context.whereConditions);

        return columns.stream()
                .filter(col -> isValidSimpleColumnName(col))
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Анализ операций соединения
     */
    private static void analyzeJoins(JsonNode node, List<Recommendation> recommendations, AnalysisContext context) {
        if (node == null || node.isMissingNode()) return;

        String nodeType = getNodeText(node, "Node Type");

        if ("Nested Loop".equals(nodeType) || "Hash Join".equals(nodeType) || "Merge Join".equals(nodeType)) {
            long actualRows = getNodeLong(node, "Actual Rows");
            double actualTime = getNodeDouble(node, "Actual Total Time");

            // Проверка Cartesian Join
            boolean hasJoinCondition = node.has("Join Filter") ||
                    node.has("Hash Cond") ||
                    node.has("Merge Cond");

            if (!hasJoinCondition) {
                addRecommendation(
                        recommendations,
                        RecommendationType.REWRITE_QUERY,
                        String.format("Обнаружен Cartesian Join (%,d строк, %.2f мс). " +
                                        "Добавьте условие соединения.",
                                actualRows, actualTime),
                        ImpactLevel.HIGH,
                        70.0,
                        generateJoinOptimizationSuggestion(context.originalQuery, node)
                );
            }

            // Проверка условий соединения для индексов
            analyzeJoinConditionsForIndexes(node, recommendations, context);
        }

        // Рекурсивный обход
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                analyzeJoins(child, recommendations, context);
            }
        }
    }

    /**
     * Анализ условий соединения для создания индексов
     */
    private static void analyzeJoinConditionsForIndexes(JsonNode node, List<Recommendation> recommendations, AnalysisContext context) {
        String condition = "";
        String conditionType = "";

        if (node.has("Join Filter")) {
            condition = getNodeText(node, "Join Filter");
            conditionType = "Join Filter";
        } else if (node.has("Hash Cond")) {
            condition = getNodeText(node, "Hash Cond");
            conditionType = "Hash Cond";
        } else if (node.has("Merge Cond")) {
            condition = getNodeText(node, "Merge Cond");
            conditionType = "Merge Cond";
        }

        if (!condition.isEmpty()) {
            List<String> joinColumns = extractColumnsFromConditionWithJSqlParser(condition);

            if (!joinColumns.isEmpty()) {
                // Определяем таблицы, участвующие в соединении
                Set<String> joinTables = findJoinTables(node, context);

                for (String table : joinTables) {
                    // Получаем колонки JOIN для этой таблицы
                    List<String> joinColumnsForTable = getJoinColumnsForTable(table, context);

                    if (!joinColumnsForTable.isEmpty()) {
                        String recommendationDesc = String.format(
                                "Условие соединения использует колонки таблицы '%s'. " +
                                        "Создайте индексы на соединяемых столбцах для улучшения производительности JOIN.",
                                table
                        );

                        addRecommendationWithIndex(
                                recommendations,
                                RecommendationType.CREATE_INDEX,
                                recommendationDesc,
                                ImpactLevel.HIGH,
                                50.0,
                                table,
                                joinColumnsForTable,
                                "JOIN_CONDITION_" + conditionType,
                                context
                        );
                    }
                }
            }
        }
    }

    /**
     * Получение колонок JOIN для конкретной таблицы
     */
    private static List<String> getJoinColumnsForTable(String tableName, AnalysisContext context) {
        // В реальной реализации нужно сопоставлять колонки с таблицами
        // Здесь возвращаем все колонки из JOIN условий
        return context.joinConditions.stream()
                .filter(col -> isValidSimpleColumnName(col))
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Поиск таблиц, участвующих в соединении
     */
    private static Set<String> findJoinTables(JsonNode node, AnalysisContext context) {
        Set<String> tables = new HashSet<>();

        // Рекурсивно ищем таблицы в дочерних узлах
        findTablesRecursive(node, tables, context);

        return tables;
    }

    private static void findTablesRecursive(JsonNode node, Set<String> tables, AnalysisContext context) {
        if (node == null || node.isMissingNode()) return;

        String nodeType = getNodeText(node, "Node Type");
        String relationName = getNodeText(node, "Relation Name");

        if (nodeType.contains("Scan") && !relationName.isEmpty()) {
            tables.add(relationName);
        }

        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                findTablesRecursive(child, tables, context);
            }
        }
    }

    /**
     * Анализ операций сортировки с генерацией индексов
     */
    private static void analyzeSortOperations(JsonNode node, List<Recommendation> recommendations, AnalysisContext context) {
        if (node == null || node.isMissingNode()) return;

        String nodeType = getNodeText(node, "Node Type");

        if ("Sort".equals(nodeType)) {
            String sortSpaceType = getNodeText(node, "Sort Space Type");
            String sortKey = getNodeText(node, "Sort Key");
            String sortMethod = getNodeText(node, "Sort Method");
            long actualRows = getNodeLong(node, "Actual Rows");
            double actualTime = getNodeDouble(node, "Actual Total Time");

            // Определяем таблицу для сортировки
            String targetTable = findTableForSort(node, context);

            if (!targetTable.isEmpty() && !sortKey.isEmpty()) {
                List<String> sortColumns = extractColumnsFromSortKey(sortKey);

                if (!sortColumns.isEmpty()) {
                    // Сортировка на диске
                    if ("Disk".equals(sortSpaceType)) {
                        String recommendationDesc = String.format(
                                "Сортировка %,d строк использовала диск (%.2f мс, метод: %s). " +
                                        "Создайте индекс для избежания сортировки.",
                                actualRows, actualTime, sortMethod
                        );

                        addRecommendationWithIndex(
                                recommendations,
                                RecommendationType.CREATE_INDEX,
                                recommendationDesc,
                                actualRows > 10000 ? ImpactLevel.HIGH : ImpactLevel.MEDIUM,
                                45.0,
                                targetTable,
                                sortColumns,
                                "SORT_OPTIMIZATION",
                                context
                        );
                    }

                    // Сортировка без использования индекса
                    if (actualRows > 5000) {
                        String recommendationDesc = String.format(
                                "Сортировка %,d строк по %s. " +
                                        "Рассмотрите создание индекса для избежания сортировки.",
                                actualRows, abbreviateText(sortKey, 100)
                        );

                        addRecommendationWithIndex(
                                recommendations,
                                RecommendationType.CREATE_INDEX,
                                recommendationDesc,
                                actualRows > 50000 ? ImpactLevel.HIGH : ImpactLevel.MEDIUM,
                                40.0,
                                targetTable,
                                sortColumns,
                                "SORT_INDEX",
                                context
                        );
                    }
                }
            }
        }

        // Рекурсивный обход
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                analyzeSortOperations(child, recommendations, context);
            }
        }
    }

    /**
     * Поиск таблицы для операции сортировки
     */
    private static String findTableForSort(JsonNode node, AnalysisContext context) {
        if (node == null || node.isMissingNode()) return "";

        // Ищем ближайшую таблицу вверх по дереву
        JsonNode current = node;
        while (current != null) {
            String nodeType = getNodeText(current, "Node Type");
            String relationName = getNodeText(current, "Relation Name");

            if (nodeType.contains("Scan") && !relationName.isEmpty()) {
                return relationName;
            }

            // Переходим к родительскому узлу - ищем Plans выше
            // В упрощенной реализации ищем в контексте
            if (context.scanNodes.size() > 0) {
                return getNodeText(context.scanNodes.get(0), "Relation Name");
            }
            break;
        }

        return "";
    }

    /**
     * Генерация оптимизированных версий запроса
     */
    private static void generateOptimizedQueries(List<Recommendation> recommendations, AnalysisContext context) {
        if (context.originalQuery == null || context.originalQuery.isEmpty()) return;

        // 1. Оптимизация с добавлением индексов
        generateIndexOptimizations(recommendations, context);

        // 2. Оптимизация с переписыванием запроса
        generateQueryRewrites(recommendations, context);

        // 3. Оптимизация с добавлением LIMIT
        generateLimitOptimizations(recommendations, context);

        // 4. Оптимизация с использованием временных таблиц
        generateTempTableOptimizations(recommendations, context);
    }

    /**
     * Генерация оптимизаций с индексами
     */
    private static void generateIndexOptimizations(List<Recommendation> recommendations, AnalysisContext context) {
        // Собираем все предложения по индексам
        Map<String, List<List<String>>> tableIndexColumns = new HashMap<>();

        for (Recommendation rec : recommendations) {
            if (rec.getType() == RecommendationType.CREATE_INDEX && rec.getSqlCommand() != null) {
                String sql = rec.getSqlCommand();
                // Извлекаем имя таблицы и колонки из SQL команды
                String tableName = extractTableNameFromIndexCommand(sql);
                List<String> columns = extractColumnsFromIndexCommand(sql);
                if (tableName != null && !columns.isEmpty()) {
                    tableIndexColumns.computeIfAbsent(tableName, k -> new ArrayList<>()).add(columns);
                }
            }
        }

        // Создаем рекомендации по созданию составных индексов
        for (Map.Entry<String, List<List<String>>> entry : tableIndexColumns.entrySet()) {
            String tableName = entry.getKey();
            List<List<String>> allColumnsList = entry.getValue();

            if (allColumnsList.size() >= 2) {
                // Объединяем все колонки из разных рекомендаций
                Set<String> allColumns = new LinkedHashSet<>();
                for (List<String> columns : allColumnsList) {
                    allColumns.addAll(columns);
                }

                // Создаем составной индекс (максимум 3 колонки)
                List<String> combinedColumns = new ArrayList<>(allColumns);
                if (combinedColumns.size() > 3) {
                    combinedColumns = combinedColumns.subList(0, 3);
                }

                if (!combinedColumns.isEmpty()) {
                    String combinedIndexCommand = String.format(
                            "CREATE INDEX idx_%s_combined ON %s(%s);",
                            tableName.toLowerCase(),
                            tableName,
                            String.join(", ", combinedColumns)
                    );

                    addRecommendation(
                            recommendations,
                            RecommendationType.CREATE_INDEX,
                            String.format("Множественные условия на таблице '%s'. " +
                                    "Рассмотрите создание составного индекса.", tableName),
                            ImpactLevel.HIGH,
                            60.0,
                            combinedIndexCommand
                    );
                }
            }
        }
    }

    /**
     * Извлечение колонок из команды создания индекса
     */
    private static List<String> extractColumnsFromIndexCommand(String sqlCommand) {
        List<String> columns = new ArrayList<>();
        if (sqlCommand == null) return columns;

        // Паттерн: CREATE INDEX ... ON table_name(col1, col2, ...)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "CREATE\\s+INDEX\\s+\\w+\\s+ON\\s+\\w+\\s*\\(([^)]+)\\)",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );

        java.util.regex.Matcher matcher = pattern.matcher(sqlCommand);
        if (matcher.find()) {
            String cols = matcher.group(1);
            String[] colArray = cols.split("\\s*,\\s*");
            for (String col : colArray) {
                col = col.trim();
                if (!col.isEmpty() && isValidSimpleColumnName(col)) {
                    columns.add(col);
                }
            }
        }

        return columns;
    }

    /**
     * Генерация переписанных версий запроса
     */
    private static void generateQueryRewrites(List<Recommendation> recommendations, AnalysisContext context) {
        String original = context.originalQuery.toUpperCase();

        // 1. Замена подзапросов на JOIN
        if (original.contains("SELECT") && original.contains("IN (SELECT") &&
                !original.contains("EXISTS")) {

            String optimizedQuery = rewriteSubqueryToJoin(context.originalQuery);
            if (optimizedQuery != null) {
                addRecommendation(
                        recommendations,
                        RecommendationType.REWRITE_QUERY,
                        "Подзапрос IN (SELECT ...) может быть заменен на JOIN для лучшей производительности.",
                        ImpactLevel.MEDIUM,
                        40.0,
                        optimizedQuery
                );
            }
        }

        // 2. Замена OR на UNION
        if (original.contains(" OR ") && original.contains("WHERE")) {
            String optimizedQuery = rewriteOrToUnion(context.originalQuery);
            if (optimizedQuery != null) {
                addRecommendation(
                        recommendations,
                        RecommendationType.REWRITE_QUERY,
                        "Условие OR может быть заменено на UNION для использования индексов.",
                        ImpactLevel.MEDIUM,
                        35.0,
                        optimizedQuery
                );
            }
        }

        // 3. Оптимизация JOIN порядка
        if (original.contains("JOIN") && context.joinNodes.size() > 2) {
            String optimizedQuery = optimizeJoinOrder(context.originalQuery);
            if (optimizedQuery != null) {
                addRecommendation(
                        recommendations,
                        RecommendationType.REWRITE_QUERY,
                        "Порядок JOIN может быть оптимизирован для лучшей производительности.",
                        ImpactLevel.LOW,
                        25.0,
                        optimizedQuery
                );
            }
        }
    }

    /**
     * Генерация оптимизаций с LIMIT
     */
    private static void generateLimitOptimizations(List<Recommendation> recommendations, AnalysisContext context) {
        String original = context.originalQuery.toUpperCase();

        if (!original.contains("LIMIT") && !original.contains("WHERE ROWNUM") &&
                context.metrics.actualRows > 1000) {

            String optimizedQuery = context.originalQuery + " LIMIT 100";

            addRecommendation(
                    recommendations,
                    RecommendationType.ADD_LIMIT,
                    "Запрос возвращает много строк без LIMIT. " +
                            "Добавьте LIMIT для ограничения результата.",
                    ImpactLevel.LOW,
                    15.0,
                    optimizedQuery
            );
        }
    }

    /**
     * Генерация оптимизаций с временными таблицами
     */
    private static void generateTempTableOptimizations(List<Recommendation> recommendations, AnalysisContext context) {
        if (context.metrics.actualRows > 100000 && context.originalQuery.contains("SELECT")) {
            String tempTableQuery = generateTempTableVersion(context.originalQuery);

            addRecommendation(
                    recommendations,
                    RecommendationType.USE_TEMP_TABLE,
                    "Большой запрос может быть оптимизирован с использованием временных таблиц.",
                    ImpactLevel.MEDIUM,
                    30.0,
                    tempTableQuery
            );
        }
    }

    /**
     * Анализ операций агрегации
     */
    private static void analyzeAggregations(JsonNode node, List<Recommendation> recommendations, AnalysisContext context) {
        if (node == null || node.isMissingNode()) return;

        String nodeType = getNodeText(node, "Node Type");

        if ("Aggregate".equals(nodeType) || "GroupAggregate".equals(nodeType) || "HashAggregate".equals(nodeType)) {
            long actualRows = getNodeLong(node, "Actual Rows");
            double actualTime = getNodeDouble(node, "Actual Total Time");
            String strategy = getNodeText(node, "Strategy");

            // Проверка агрегации большого количества строк
            if (actualRows > 100000 && actualTime > 1000) {
                addRecommendation(
                        recommendations,
                        RecommendationType.REWRITE_QUERY,
                        String.format("Агрегация %,d строк заняла %.2f мс. " +
                                        "Рассмотрите предварительную фильтрацию.",
                                actualRows, actualTime),
                        actualTime > 5000 ? ImpactLevel.HIGH : ImpactLevel.MEDIUM,
                        35.0,
                        null
                );
            }

            // Проверка HashAggregate с дисковыми операциями
            if ("HashAggregate".equals(nodeType) && context.metrics.tempWrittenBlocks > 0) {
                addRecommendation(
                        recommendations,
                        RecommendationType.INCREASE_WORK_MEM,
                        "HashAggregate использует дисковые операции. " +
                                "Увеличьте work_mem.",
                        ImpactLevel.MEDIUM,
                        45.0,
                        null
                );
            }

            // Проверка агрегации без группировки
            if ("Plain".equals(strategy) && actualRows > 1000) {
                addRecommendation(
                        recommendations,
                        RecommendationType.ADD_LIMIT,
                        "Агрегация без группировки возвращает много строк. " +
                                "Добавьте LIMIT или более строгий фильтр.",
                        ImpactLevel.LOW,
                        15.0,
                        null
                );
            }

            // Проверка GroupAggregate без индекса
            if ("GroupAggregate".equals(nodeType) && !node.has("Group Key")) {
                addRecommendation(
                        recommendations,
                        RecommendationType.CREATE_INDEX,
                        "GroupAggregate без явного Group Key. " +
                                "Рассмотрите создание индекса для группировки.",
                        ImpactLevel.MEDIUM,
                        30.0,
                        null
                );
            }
        }

        // Рекурсивный обход
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                analyzeAggregations(child, recommendations, context);
            }
        }
    }

    /**
     * Анализ использования памяти и буферов
     */
    private static void analyzeMemoryUsage(JsonNode node, List<Recommendation> recommendations, AnalysisContext context) {
        if (node == null || node.isMissingNode()) return;

        // Анализ использования буферов
        long sharedHitBlocks = getNodeLong(node, "Shared Hit Blocks");
        long sharedReadBlocks = getNodeLong(node, "Shared Read Blocks");
        long tempReadBlocks = getNodeLong(node, "Temp Read Blocks");
        long tempWrittenBlocks = getNodeLong(node, "Temp Written Blocks");

        // Анализ кэширования
        if (sharedReadBlocks > 0) {
            double hitRatio = (double) sharedHitBlocks / (sharedHitBlocks + sharedReadBlocks);

            if (hitRatio < CACHE_HIT_RATIO_THRESHOLD) {
                String nodeType = getNodeText(node, "Node Type");
                String objectName = getNodeText(node, "Relation Name");

                if (objectName.isEmpty()) {
                    objectName = nodeType;
                }

                addRecommendation(
                        recommendations,
                        RecommendationType.TUNE_DB_PARAMS,
                        String.format("Низкий коэффициент попадания в кэш (%.1f%%) для %s. " +
                                        "Увеличьте shared_buffers.",
                                hitRatio * 100, objectName),
                        hitRatio < 0.5 ? ImpactLevel.HIGH : ImpactLevel.MEDIUM,
                        (1 - hitRatio) * 50.0,
                        null
                );
            }
        }

        // Анализ временных операций
        if (tempReadBlocks > 0 || tempWrittenBlocks > 0) {
            addRecommendation(
                    recommendations,
                    RecommendationType.INCREASE_WORK_MEM,
                    String.format("Обнаружены дисковые временные операции: %d read, %d write. " +
                                    "Увеличьте work_mem или temp_buffers.",
                            tempReadBlocks, tempWrittenBlocks),
                    (tempReadBlocks + tempWrittenBlocks) > 1000 ? ImpactLevel.HIGH : ImpactLevel.MEDIUM,
                    40.0,
                    null
            );
        }

        // Анализ локальных буферов
        long localHitBlocks = getNodeLong(node, "Local Hit Blocks");
        long localReadBlocks = getNodeLong(node, "Local Read Blocks");

        if (localReadBlocks > HIGH_BUFFER_THRESHOLD) {
            addRecommendation(
                    recommendations,
                    RecommendationType.TUNE_DB_PARAMS,
                    "Высокое использование локальных буферов. " +
                            "Проверьте настройки temp_buffers.",
                    ImpactLevel.MEDIUM,
                    25.0,
                    null
            );
        }

        // Рекурсивный обход
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                analyzeMemoryUsage(child, recommendations, context);
            }
        }
    }

    /**
     * Анализ параллельного выполнения
     */
    private static void analyzeParallelism(JsonNode node, List<Recommendation> recommendations, AnalysisContext context) {
        if (node == null || node.isMissingNode()) return;

        String nodeType = getNodeText(node, "Node Type");

        // Проверка параллельных операций
        if (!node.path("Workers").isMissingNode()) {
            JsonNode workers = node.get("Workers");
            int plannedWorkers = getNodeInt(node, "Workers Planned");
            int launchedWorkers = getNodeInt(node, "Workers Launched");

            if (workers != null && workers.isArray()) {
                context.metrics.parallelWorkerCount += launchedWorkers;

                // Анализ эффективности воркеров
                double totalWorkerTime = 0;
                double maxWorkerTime = 0;

                for (JsonNode worker : workers) {
                    double workerTime = getNodeDouble(worker, "Actual Total Time");
                    totalWorkerTime += workerTime;
                    maxWorkerTime = Math.max(maxWorkerTime, workerTime);
                }

                double efficiency = launchedWorkers > 0 ? (totalWorkerTime / launchedWorkers) / maxWorkerTime : 0;

                if (efficiency < 0.7) {
                    addRecommendation(
                            recommendations,
                            RecommendationType.TUNE_DB_PARAMS,
                            String.format("Низкая эффективность параллелизма (%.1f%%). " +
                                            "Настройте max_parallel_workers_per_gather.",
                                    efficiency * 100),
                            ImpactLevel.MEDIUM,
                            (1 - efficiency) * 30.0,
                            null
                    );
                }
            }

            // Проверка неиспользованных воркеров
            if (plannedWorkers > 0 && launchedWorkers < plannedWorkers) {
                addRecommendation(
                        recommendations,
                        RecommendationType.TUNE_DB_PARAMS,
                        String.format("Недостаточно параллельных воркеров: запланировано %d, запущено %d.",
                                plannedWorkers, launchedWorkers),
                        ImpactLevel.MEDIUM,
                        20.0,
                        null
                );
            }
        }

        // Проверка узлов, которые могли бы быть распараллелены
        if (("Seq Scan".equals(nodeType) || "Hash Join".equals(nodeType) ||
                "Aggregate".equals(nodeType) || "Sort".equals(nodeType)) &&
                node.path("Workers Planned").asInt(0) == 0) {

            long actualRows = getNodeLong(node, "Actual Rows");

            if (actualRows > 100000) {
                addRecommendation(
                        recommendations,
                        RecommendationType.ENABLE_PARALLEL,
                        String.format("Операция с %,d строками не использует параллелизм. " +
                                        "Рассмотрите настройку parallel_tuple_cost.",
                                actualRows),
                        ImpactLevel.MEDIUM,
                        30.0,
                        null
                );
            }
        }

        // Рекурсивный обход
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                analyzeParallelism(child, recommendations, context);
            }
        }
    }

    /**
     * Анализ вложенных запросов
     */
    private static void analyzeSubqueries(JsonNode node, List<Recommendation> recommendations, AnalysisContext context) {
        if (node == null || node.isMissingNode()) return;

        String nodeType = getNodeText(node, "Node Type");

        if (("Subquery Scan".equals(nodeType) || "Materialize".equals(nodeType) ||
                "CTE Scan".equals(nodeType)) &&
                getNodeLong(node, "Actual Rows") > 10000) {

            addRecommendation(
                    recommendations,
                    RecommendationType.REWRITE_QUERY,
                    String.format("%s обрабатывает %,d строк. " +
                                    "Рассмотрите переписывание в JOIN.",
                            nodeType, getNodeLong(node, "Actual Rows")),
                    ImpactLevel.MEDIUM,
                    35.0,
                    null
            );
        }

        // Проверка коррелированных подзапросов
        if ("Subquery Scan".equals(nodeType) && node.has("Filter")) {
            String filter = getNodeText(node, "Filter");
            if (filter.contains("Param")) {
                addRecommendation(
                        recommendations,
                        RecommendationType.REWRITE_QUERY,
                        "Обнаружен коррелированный подзапрос. " +
                                "Рассмотрите переписывание в JOIN.",
                        ImpactLevel.HIGH,
                        45.0,
                        null
                );
            }
        }

        // Рекурсивный обход
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                analyzeSubqueries(child, recommendations, context);
            }
        }
    }

    /**
     * Анализ временных таблиц
     */
    private static void analyzeTempTables(JsonNode node, List<Recommendation> recommendations, AnalysisContext context) {
        if (node == null || node.isMissingNode()) return;

        String nodeType = getNodeText(node, "Node Type");

        if (("Materialize".equals(nodeType) || "Hash".equals(nodeType) ||
                "Sort".equals(nodeType)) &&
                getNodeLong(node, "Actual Rows") > 50000) {

            addRecommendation(
                    recommendations,
                    RecommendationType.USE_TEMP_TABLE,
                    String.format("Материализация %,d строк во временную структуру. " +
                                    "Рассмотрите использование временной таблицы.",
                            getNodeLong(node, "Actual Rows")),
                    ImpactLevel.MEDIUM,
                    25.0,
                    null
            );
        }

        // Проверка Hash узлов с дисковыми операциями
        if ("Hash".equals(nodeType) && context.metrics.tempWrittenBlocks > 0) {
            addRecommendation(
                    recommendations,
                    RecommendationType.INCREASE_WORK_MEM,
                    "Hash операция использует диск. " +
                            "Увеличьте work_mem.",
                    ImpactLevel.MEDIUM,
                    40.0,
                    null
            );
        }

        // Рекурсивный обход
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                analyzeTempTables(child, recommendations, context);
            }
        }
    }

    /**
     * Анализ использования индексов
     */
    private static void analyzeIndexUsage(JsonNode node, List<Recommendation> recommendations, AnalysisContext context) {
        if (node == null || node.isMissingNode()) return;

        String nodeType = getNodeText(node, "Node Type");

        // Проверка неиспользованных индексов
        if (("Seq Scan".equals(nodeType) || "Bitmap Heap Scan".equals(nodeType)) &&
                node.has("Relation Name")) {

            String tableName = getNodeText(node, "Relation Name");
            String filter = getNodeText(node, "Filter");

            if (!filter.isEmpty() && isIndexableCondition(filter)) {
                addRecommendation(
                        recommendations,
                        RecommendationType.CREATE_INDEX,
                        String.format("Таблица '%s' сканируется без индекса, но имеет фильтруемые условия. " +
                                "Рассмотрите создание индекса.", tableName),
                        ImpactLevel.MEDIUM,
                        35.0,
                        null
                );
            }
        }

        // Проверка Index Only Scan
        if ("Index Only Scan".equals(nodeType)) {
            context.metrics.indexOnlyScanCount++;

            // Проверка, все ли необходимые колонки в индексе
            if (node.has("Heap Fetches") && getNodeLong(node, "Heap Fetches") > 0) {
                addRecommendation(
                        recommendations,
                        RecommendationType.CREATE_INDEX,
                        "Index Only Scan выполняет Heap Fetches. " +
                                "Рассмотрите добавление колонок в индекс.",
                        ImpactLevel.LOW,
                        15.0,
                        null
                );
            }
        }

        // Проверка повторяющихся индексов
        if (nodeType.contains("Index Scan") && node.has("Index Name")) {
            String indexName = getNodeText(node, "Index Name");
            if (!context.metrics.usedIndexes.contains(indexName)) {
                context.metrics.usedIndexes.add(indexName);
            } else {
                addRecommendation(
                        recommendations,
                        RecommendationType.REWRITE_QUERY,
                        String.format("Индекс '%s' используется многократно. " +
                                        "Проверьте возможность консолидации индексов.",
                                indexName),
                        ImpactLevel.LOW,
                        10.0,
                        null
                );
            }
        }

        // Рекурсивный обход
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                analyzeIndexUsage(child, recommendations, context);
            }
        }
    }

    /**
     * Общий анализ производительности
     */
    private static void analyzeOverallPerformance(JsonNode node, List<Recommendation> recommendations, AnalysisContext context) {
        if (context.metrics.totalCost == 0 && context.metrics.actualTotalTime == 0) return;

        // Анализ соотношения Seq Scan vs Index Scan
        if (context.metrics.seqScanCount > 0) {
            double totalScans = context.metrics.seqScanCount + context.metrics.indexScanCount;
            double seqScanRatio = totalScans > 0 ? context.metrics.seqScanCount / totalScans : 0;

            if (seqScanRatio > 0.7) {
                addRecommendation(
                        recommendations,
                        RecommendationType.UPDATE_STATISTICS,
                        String.format("Высокое соотношение Seq Scan (%.1f%%). " +
                                        "Обновите статистику таблиц.",
                                seqScanRatio * 100),
                        ImpactLevel.MEDIUM,
                        20.0,
                        null
                );
            }
        }

        // Анализ использования диска для сортировки
        if (context.metrics.sortCount > 0) {
            double diskSortRatio = (double) context.metrics.diskSortCount / context.metrics.sortCount;

            if (diskSortRatio > 0.3) {
                addRecommendation(
                        recommendations,
                        RecommendationType.TUNE_DB_PARAMS,
                        String.format("Высокий процент сортировок на диске (%.1f%%). " +
                                        "Настройте work_mem и maintenance_work_mem.",
                                diskSortRatio * 100),
                        ImpactLevel.HIGH,
                        diskSortRatio * 50.0,
                        null
                );
            }
        }

        // Анализ общего времени выполнения
        if (context.metrics.actualTotalTime > HIGH_EXECUTION_TIME_MS) {
            addRecommendation(
                    recommendations,
                    RecommendationType.SPLIT_QUERY,
                    String.format("Общее время выполнения %.2f мс. " +
                                    "Рассмотрите разбиение запроса на части.",
                            context.metrics.actualTotalTime),
                    context.metrics.actualTotalTime > 30000 ? ImpactLevel.HIGH : ImpactLevel.MEDIUM,
                    Math.min(context.metrics.actualTotalTime / 1000.0, 50.0),
                    null
            );
        }

        // Анализ большого количества JOIN
        if (context.metrics.joinCount > MAX_JOIN_COUNT) {
            addRecommendation(
                    recommendations,
                    RecommendationType.REWRITE_QUERY,
                    String.format("Запрос содержит %d операций JOIN. " +
                                    "Рассмотрите упрощение логики запроса.",
                            context.metrics.joinCount),
                    ImpactLevel.MEDIUM,
                    Math.min(context.metrics.joinCount * 5.0, 40.0),
                    null
            );
        }

        // Анализ точности планировщика
        if (context.metrics.actualRows > 0 && context.metrics.plannedRows > 0) {
            double accuracyRatio = (double) context.metrics.actualRows / context.metrics.plannedRows;

            if (accuracyRatio < 0.1 || accuracyRatio > 10) {
                addRecommendation(
                        recommendations,
                        RecommendationType.UPDATE_STATISTICS,
                        String.format("Неточная оценка планировщика: запланировано %,d, фактически %,d (x%.1f). " +
                                        "Обновите статистику.",
                                context.metrics.plannedRows, context.metrics.actualRows, accuracyRatio),
                        ImpactLevel.MEDIUM,
                        25.0,
                        null
                );
            }
        }

        // Анализ глубины плана
        if (context.metrics.maxDepth > 10) {
            addRecommendation(
                    recommendations,
                    RecommendationType.REWRITE_QUERY,
                    String.format("Большая глубина плана выполнения (%d уровней). " +
                                    "Рассмотрите упрощение запроса.",
                            context.metrics.maxDepth),
                    ImpactLevel.MEDIUM,
                    Math.min(context.metrics.maxDepth * 3.0, 30.0),
                    null
            );
        }

        // Анализ количества узлов
        if (context.metrics.nodeCount > 50) {
            addRecommendation(
                    recommendations,
                    RecommendationType.SPLIT_QUERY,
                    String.format("Сложный план выполнения (%d узлов). " +
                                    "Рассмотрите разбиение на несколько запросов.",
                            context.metrics.nodeCount),
                    ImpactLevel.MEDIUM,
                    Math.min(context.metrics.nodeCount / 2.0, 35.0),
                    null
            );
        }
    }

    /**
     * Анализ статистики
     */
    private static void analyzeStatistics(JsonNode node, List<Recommendation> recommendations, AnalysisContext context) {
        // Проверка устаревшей статистики
        if (context.metrics.actualRows > 0 && context.metrics.plannedRows > 0) {
            double diff = Math.abs(context.metrics.actualRows - context.metrics.plannedRows);
            double avg = (context.metrics.actualRows + context.metrics.plannedRows) / 2.0;

            if (avg > 0 && diff / avg > 2.0) {
                addRecommendation(
                        recommendations,
                        RecommendationType.UPDATE_STATISTICS,
                        "Значительное расхождение между запланированными и фактическими строками. " +
                                "Статистика может быть устаревшей.",
                        ImpactLevel.HIGH,
                        40.0,
                        null
                );
            }
        }

        // Проверка корреляции данных
        if (context.metrics.seqScanCount > 0 && context.metrics.indexScanCount == 0 &&
                context.metrics.filterCount > 0 && context.metrics.nullFilterCount > 0) {
            addRecommendation(
                    recommendations,
                    RecommendationType.UPDATE_STATISTICS,
                    "Частые фильтры по NULL значениям без использования индексов. " +
                            "Проверьте статистику NULL значений.",
                    ImpactLevel.LOW,
                    15.0,
                    null
            );
        }
    }

    /**
     * Анализ рекурсивных операций
     */
    private static void analyzeRecursiveOperations(JsonNode node, List<Recommendation> recommendations, AnalysisContext context) {
        if (context.metrics.recursiveUnionCount > 0) {
            addRecommendation(
                    recommendations,
                    RecommendationType.TUNE_DB_PARAMS,
                    "Запрос содержит рекурсивные операции. " +
                            "Настройте work_mem для рекурсивных запросов.",
                    ImpactLevel.MEDIUM,
                    25.0,
                    null
            );
        }
    }

    /**
     * Анализ оконных функций
     */
    private static void analyzeWindowFunctions(JsonNode node, List<Recommendation> recommendations, AnalysisContext context) {
        if (context.metrics.windowFunctionCount > 0) {
            addRecommendation(
                    recommendations,
                    RecommendationType.TUNE_DB_PARAMS,
                    "Запрос содержит оконные функции. " +
                            "Настройте work_mem для сортировки в рамках окон.",
                    ImpactLevel.MEDIUM,
                    20.0,
                    null
            );
        }
    }

    /**
     * Анализ блокировок
     */
    private static void analyzeLockingBehavior(JsonNode node, List<Recommendation> recommendations, AnalysisContext context) {
        // Анализ блокировок на уровне строк (эвристика)
        if (context.metrics.actualLoops > 1000 && context.metrics.joinCount > 2) {
            addRecommendation(
                    recommendations,
                    RecommendationType.REWRITE_QUERY,
                    "Множественные циклы в запросе могут вызывать блокировки. " +
                            "Рассмотрите оптимизацию уровня изоляции.",
                    ImpactLevel.LOW,
                    10.0,
                    null
            );
        }
    }

    /**
     * Добавление рекомендации с генерацией SQL команды для индекса
     */
    private static void addRecommendationWithIndex(List<Recommendation> recommendations,
                                                   RecommendationType type,
                                                   String description,
                                                   ImpactLevel impact,
                                                   Double estimatedImprovement,
                                                   String tableName,
                                                   List<String> columns,
                                                   String indexType,
                                                   AnalysisContext context) {

        if (columns == null || columns.isEmpty()) {
            // Не создаем рекомендацию CREATE_INDEX без колонок
            return;
        }

        // Фильтруем только валидные колонки
        List<String> validColumns = columns.stream()
                .filter(col -> isValidSimpleColumnName(col))
                .distinct()
                .collect(Collectors.toList());

        if (validColumns.isEmpty()) {
            return;
        }

        // Генерируем SQL команду
        String sqlCommand = generateIndexSqlCommand(tableName, validColumns);

        addRecommendation(
                recommendations,
                type,
                description,
                impact,
                estimatedImprovement,
                sqlCommand
        );
    }

    /**
     * Генерация SQL команды для создания индекса
     */
    private static String generateIndexSqlCommand(String tableName, List<String> columns) {
        if (tableName == null || tableName.isEmpty() || columns == null || columns.isEmpty()) {
            return null;
        }

        // Фильтруем только валидные колонки
        List<String> validColumns = columns.stream()
                .filter(col -> isValidSimpleColumnName(col))
                .distinct()
                .collect(Collectors.toList());

        if (validColumns.isEmpty()) {
            return null;
        }

        // Генерируем имя индекса
        String indexName = generateIndexName(tableName, validColumns, "auto");
        String columnsStr = String.join(", ", validColumns);

        return String.format("CREATE INDEX %s ON %s(%s);", indexName, tableName, columnsStr);
    }

    /**
     * Генерация имени индекса
     */
    private static String generateIndexName(String tableName, List<String> columns, String indexType) {
        if (tableName == null || tableName.isEmpty()) {
            return "idx_unknown";
        }

        String baseName = "idx_" + tableName.toLowerCase().replaceAll("[^a-z0-9_]", "_");

        if (columns.size() == 1) {
            baseName += "_" + columns.get(0).toLowerCase();
        } else if (columns.size() <= 3) {
            String colPart = columns.stream()
                    .limit(3)
                    .map(col -> col.toLowerCase().replaceAll("[^a-z0-9_]", ""))
                    .collect(Collectors.joining("_"));
            baseName += "_" + colPart;
        } else {
            baseName += "_combined";
        }

        // Ограничиваем длину имени (PostgreSQL ограничение 63 символа)
        if (baseName.length() > 60) {
            baseName = baseName.substring(0, 60);
        }

        return baseName;
    }

    /**
     * Добавление рекомендации с SQL командой
     */
    private static void addRecommendation(List<Recommendation> recommendations,
                                          RecommendationType type,
                                          String description,
                                          ImpactLevel impact,
                                          Double estimatedImprovement,
                                          String sqlCommand) {

        Recommendation rec = Recommendation.builder()
                .type(type)
                .description(description)
                .impact(impact)
                .estimatedImprovement(estimatedImprovement)
                .sqlSuggestion(generateSqlSuggestion(type, description))
                .sqlCommand(sqlCommand)
                .applied(false)
                .actualImprovement(0.0)
                .warnings(new ArrayList<>())
                .metrics(new HashMap<>())
                .build();

        recommendations.add(rec);
    }

    /**
     * Сортировка и удаление дубликатов рекомендаций
     */
    private static List<Recommendation> sortAndDeduplicateRecommendations(List<Recommendation> recommendations, PlanMetrics metrics) {
        Map<String, Recommendation> uniqueRecs = new LinkedHashMap<>();

        // Сначала собираем уникальные рекомендации по SQL команде
        for (Recommendation rec : recommendations) {
            String key = rec.getSqlCommand() != null ?
                    rec.getType() + ":" + rec.getSqlCommand() :
                    rec.getType() + ":" + rec.getDescription().substring(0, Math.min(100, rec.getDescription().length()));
            uniqueRecs.putIfAbsent(key, rec);
        }

        // Сортируем по приоритету (Impact * EstimatedImprovement)
        List<Recommendation> sorted = new ArrayList<>(uniqueRecs.values());
        sorted.sort((r1, r2) -> {
            double priority1 = getImpactValue(r1.getImpact()) * (r1.getEstimatedImprovement() != null ? r1.getEstimatedImprovement() : 0);
            double priority2 = getImpactValue(r2.getImpact()) * (r2.getEstimatedImprovement() != null ? r2.getEstimatedImprovement() : 0);
            return Double.compare(priority2, priority1); // По убыванию
        });

        // Ограничиваем количество рекомендаций
        return sorted.size() > 20 ? sorted.subList(0, 20) : sorted;
    }

    private static double getImpactValue(ImpactLevel impact) {
        switch (impact) {
            case HIGH: return 3.0;
            case MEDIUM: return 2.0;
            case LOW: return 1.0;
            default: return 1.0;
        }
    }

    private static String abbreviateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    private static String getNodeText(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || !node.has(fieldName)) {
            return "";
        }
        JsonNode field = node.get(fieldName);
        return field.isTextual() ? field.asText() : field.isNumber() ? String.valueOf(field.asDouble()) : "";
    }

    private static long getNodeLong(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || !node.has(fieldName)) {
            return 0L;
        }
        JsonNode field = node.get(fieldName);
        return field.isNumber() ? field.asLong() : 0L;
    }

    private static int getNodeInt(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || !node.has(fieldName)) {
            return 0;
        }
        JsonNode field = node.get(fieldName);
        return field.isNumber() ? field.asInt() : 0;
    }

    private static double getNodeDouble(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || !node.has(fieldName)) {
            return 0.0;
        }
        JsonNode field = node.get(fieldName);
        return field.isNumber() ? field.asDouble() : 0.0;
    }

    /**
     * Проверяет, является ли условие индексируемым
     */
    private static boolean isIndexableCondition(String filter) {
        if (filter == null || filter.isEmpty()) return false;

        String cleanFilter = filter.toLowerCase();

        // Проверяем наличие операторов, которые могут использовать индексы
        return cleanFilter.contains("=") ||
                cleanFilter.contains(">") ||
                cleanFilter.contains("<") ||
                cleanFilter.contains(">=") ||
                cleanFilter.contains("<=") ||
                cleanFilter.contains("between") ||
                (cleanFilter.contains("like") && !cleanFilter.contains("%'") && cleanFilter.contains("'%")) ||
                cleanFilter.contains("in (") ||
                cleanFilter.contains("is null");
    }

    /**
     * Извлечение имени таблицы из команды создания индекса
     */
    private static String extractTableNameFromIndexCommand(String sqlCommand) {
        if (sqlCommand == null) return null;

        // Паттерн: CREATE INDEX ... ON table_name(...)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "CREATE\\s+INDEX\\s+\\w+\\s+ON\\s+(\\w+)\\s*\\(",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );

        java.util.regex.Matcher matcher = pattern.matcher(sqlCommand);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Переписывание подзапроса IN в JOIN
     */
    private static String rewriteSubqueryToJoin(String originalQuery) {
        try {
            Statement statement = CCJSqlParserUtil.parse(originalQuery);
            // Здесь должна быть сложная логика переписывания
            // Для простоты возвращаем шаблон
            return "-- Оптимизированная версия с JOIN:\n" +
                    "-- SELECT t1.* FROM table1 t1\n" +
                    "-- INNER JOIN table2 t2 ON t1.col = t2.col\n" +
                    "-- WHERE ... (условия из подзапроса)";
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Переписывание OR в UNION
     */
    private static String rewriteOrToUnion(String originalQuery) {
        return "-- Оптимизированная версия с UNION:\n" +
                "-- SELECT * FROM table WHERE condition1\n" +
                "-- UNION\n" +
                "-- SELECT * FROM table WHERE condition2\n" +
                "-- UNION ...";
    }

    /**
     * Оптимизация порядка JOIN
     */
    private static String optimizeJoinOrder(String originalQuery) {
        return "-- Оптимизированный порядок JOIN:\n" +
                "-- Начните с самой маленькой таблицы\n" +
                "-- Используйте индексы для соединений\n" +
                "-- Учитывайте селективность условий";
    }

    /**
     * Генерация версии с временной таблицей
     */
    private static String generateTempTableVersion(String originalQuery) {
        return "-- Оптимизация с временной таблицей:\n" +
                "-- CREATE TEMP TABLE temp_results AS \n" +
                "--   SELECT ... FROM ... WHERE ...;\n" +
                "-- CREATE INDEX ON temp_results(...);\n" +
                "-- SELECT ... FROM temp_results ...;\n" +
                "-- DROP TABLE temp_results;";
    }

    /**
     * Генерация предложения по оптимизации JOIN
     */
    private static String generateJoinOptimizationSuggestion(String originalQuery, JsonNode joinNode) {
        return "-- Оптимизация JOIN:\n" +
                "-- 1. Добавьте условие соединения (ON ...)\n" +
                "-- 2. Убедитесь, что соединяемые колонки проиндексированы\n" +
                "-- 3. Рассмотрите изменение типа JOIN";
    }

    /**
     * Генерация SQL совета в зависимости от типа рекомендации
     */
    private static String generateSqlSuggestion(RecommendationType type, String description) {
        switch (type) {
            case CREATE_INDEX:
                if (description.contains("составного")) {
                    return "-- Создание составного индекса:\n" +
                            "-- CREATE INDEX idx_table_col1_col2 ON table_name(col1, col2);\n" +
                            "-- Для условий WHERE col1 = ? AND col2 > ?";
                } else if (description.contains("функционального")) {
                    return "-- Создание функционального индекса:\n" +
                            "-- CREATE INDEX idx_table_expression ON table_name(expression);\n" +
                            "-- Например: CREATE INDEX idx_lower_name ON users(LOWER(name));";
                } else {
                    return "-- Создание индекса:\n" +
                            "-- CREATE INDEX idx_table_column ON table_name(column_name);\n" +
                            "-- Примечание: избегайте индексов на часто изменяемых столбцах";
                }

            case REWRITE_QUERY:
                return "-- Оптимизация запроса:\n" +
                        "-- 1. Используйте EXPLAIN для анализа плана\n" +
                        "-- 2. Разбейте сложный запрос на части\n" +
                        "-- 3. Используйте CTE для сложных преобразований\n" +
                        "-- 4. Проверьте возможность использования материализованных представлений";

            case ADD_LIMIT:
                return "-- Добавление LIMIT:\n" +
                        "-- SELECT * FROM table_name WHERE conditions LIMIT 100;\n" +
                        "-- Для пагинации используйте OFFSET или ключевой курсор";

            case INCREASE_WORK_MEM:
                return "-- Настройка work_mem:\n" +
                        "-- ALTER SYSTEM SET work_mem = '64MB';\n" +
                        "-- или в postgresql.conf: work_mem = 64MB\n" +
                        "-- Рекомендуется: 64MB-256MB в зависимости от нагрузки";

            case TUNE_DB_PARAMS:
                return "-- Настройка параметров PostgreSQL:\n" +
                        "-- shared_buffers = 25% от RAM\n" +
                        "-- effective_cache_size = 75% от RAM\n" +
                        "-- maintenance_work_mem = 10% от RAM\n" +
                        "-- random_page_cost = 1.1 (для SSD)";

            case CHANGE_JOIN_TYPE:
                return "-- Изменение типа JOIN:\n" +
                        "-- Nested Loop: для маленьких таблиц\n" +
                        "-- Hash Join: для больших таблиц без сортировки\n" +
                        "-- Merge Join: для отсортированных данных\n" +
                        "-- Используйте /*+ Leading(t1 t2) */ для указания порядка соединения";

            case ENABLE_PARALLEL:
                return "-- Включение параллелизма:\n" +
                        "-- ALTER TABLE table_name SET (parallel_workers = 4);\n" +
                        "-- или настройте в postgresql.conf:\n" +
                        "-- max_parallel_workers_per_gather = 4\n" +
                        "-- parallel_tuple_cost = 0.1\n" +
                        "-- parallel_setup_cost = 1000.0";

            case USE_TEMP_TABLE:
                return "-- Использование временных таблиц:\n" +
                        "-- CREATE TEMP TABLE temp_results AS \n" +
                        "--   SELECT ... FROM ... WHERE ...;\n" +
                        "-- CREATE INDEX ON temp_results(...);\n" +
                        "-- SELECT ... FROM temp_results ...;";

            case UPDATE_STATISTICS:
                return "-- Обновление статистики:\n" +
                        "-- ANALYZE table_name;\n" +
                        "-- или для всех таблиц: ANALYZE;\n" +
                        "-- Для более точной статистики:\n" +
                        "-- ALTER TABLE table_name ALTER COLUMN column_name SET STATISTICS 1000;";

            case SPLIT_QUERY:
                return "-- Разбиение запроса:\n" +
                        "-- 1. Выделите общие подзапросы в CTE\n" +
                        "-- 2. Используйте материализованные представления\n" +
                        "-- 3. Разделите на несколько запросов с промежуточными результатами\n" +
                        "-- 4. Используйте пагинацию OFFSET/LIMIT";

            default:
                return "См. документацию PostgreSQL по оптимизации запросов:\n" +
                        "-- https://www.postgresql.org/docs/current/performance-tips.html";
        }
    }

    /**
     * Создание рекомендации об ошибке
     */
    private static Recommendation createErrorRecommendation(String message) {
        return Recommendation.builder()
                .type(RecommendationType.OTHER)
                .description(message)
                .impact(ImpactLevel.HIGH)
                .estimatedImprovement(0.0)
                .sqlSuggestion("Проверьте корректность запроса и соединения с базой данных.")
                .applied(false)
                .warnings(List.of("Не удалось проанализировать план выполнения"))
                .build();
    }

    /**
     * Внутренний класс для сбора метрик плана
     */
    private static class PlanMetrics {
        // Счетчики операций
        long nodeCount = 0;
        long seqScanCount = 0;
        long indexScanCount = 0;
        long indexOnlyScanCount = 0;
        long bitmapScanCount = 0;
        long joinCount = 0;
        long nestedLoopCount = 0;
        long hashJoinCount = 0;
        long mergeJoinCount = 0;
        long sortCount = 0;
        long memorySortCount = 0;
        long diskSortCount = 0;
        long aggregateCount = 0;
        long windowFunctionCount = 0;
        long cteScanCount = 0;
        long subqueryCount = 0;
        long materializeCount = 0;
        long hashCount = 0;
        long limitCount = 0;
        long uniqueCount = 0;
        long setOpCount = 0;
        long appendCount = 0;
        long recursiveUnionCount = 0;

        // Фильтры и условия
        long filterCount = 0;
        long joinFilterCount = 0;
        long indexConditionCount = 0;
        long nullFilterCount = 0;

        // Статистика строк
        long actualRows = 0;
        long plannedRows = 0;
        long maxSeqScanRows = 0;
        long maxJoinRows = 0;
        long diskSortRows = 0;

        // Буферы и память
        long sharedHitBlocks = 0;
        long sharedReadBlocks = 0;
        long sharedDirtiedBlocks = 0;
        long sharedWrittenBlocks = 0;
        long localHitBlocks = 0;
        long localReadBlocks = 0;
        long localDirtiedBlocks = 0;
        long localWrittenBlocks = 0;
        long tempReadBlocks = 0;
        long tempWrittenBlocks = 0;

        // Время и стоимость
        double totalCost = 0.0;
        double startupCost = 0.0;
        double actualTotalTime = 0.0;
        long actualLoops = 0;

        // Параллелизм
        int parallelWorkerCount = 0;

        // Структура плана
        int maxDepth = 0;

        // Дополнительная информация
        String largestSeqScanTable = "";
        Set<String> usedIndexes = new HashSet<>();
        Set<String> sortKeyColumns = new HashSet<>();
    }
}