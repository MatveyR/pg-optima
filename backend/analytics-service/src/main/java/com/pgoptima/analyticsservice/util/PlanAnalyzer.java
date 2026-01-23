package com.pgoptima.analyticsservice.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.pgoptima.shareddto.enums.ImpactLevel;
import com.pgoptima.shareddto.enums.RecommendationType;
import com.pgoptima.shareddto.response.Recommendation;

import java.util.*;

public class PlanAnalyzer {

    // Константы для пороговых значений анализа
    private static final long SEQ_SCAN_THRESHOLD = 1000;
    private static final double HIGH_COST_RATIO = 0.3;
    private static final long HIGH_BUFFER_THRESHOLD = 10000;
    private static final double PARALLEL_EFFECTIVENESS_THRESHOLD = 1.5;

    /**
     * Основной метод анализа плана выполнения.
     * Собирает все рекомендации по оптимизации на основе анализа плана.
     */
    public static List<Recommendation> analyze(JsonNode plan) {
        List<Recommendation> recommendations = new ArrayList<>();

        if (plan == null) {
            return recommendations;
        }

        // Собираем метрики для контекстного анализа
        PlanMetrics metrics = collectMetrics(plan);

        // 1. Анализ сканирования таблиц
        analyzeScans(plan, recommendations, metrics);

        // 2. Анализ операций соединения (JOIN)
        analyzeJoins(plan, recommendations, metrics);

        // 3. Анализ операций агрегации
        analyzeAggregations(plan, recommendations);

        // 4. Анализ операций сортировки
        analyzeSortOperations(plan, recommendations);

        // 5. Анализ использования памяти
        analyzeMemoryUsage(plan, recommendations);

        // 6. Анализ параллельного выполнения
        analyzeParallelism(plan, recommendations);

        // 7. Анализ вложенных запросов
        analyzeSubqueries(plan, recommendations);

        // 8. Анализ временных таблиц
        analyzeTempTables(plan, recommendations);

        // 9. Общий анализ производительности
        analyzeOverallPerformance(plan, recommendations, metrics);

        // Удаляем дубликаты рекомендаций
        return deduplicateRecommendations(recommendations);
    }

    /**
     * Сбор метрик плана выполнения для контекстного анализа.
     * Возвращает структурированные данные о производительности плана.
     */
    private static PlanMetrics collectMetrics(JsonNode node) {
        PlanMetrics metrics = new PlanMetrics();
        collectMetricsRecursive(node, metrics);
        return metrics;
    }

    /**
     * Рекурсивный сбор метрик из всех узлов плана.
     */
    private static void collectMetricsRecursive(JsonNode node, PlanMetrics metrics) {
        if (node == null) return;

        String nodeType = node.get("Node Type").asText();

        // Собираем общие метрики
        metrics.totalCost += node.path("Total Cost").asDouble(0);
        metrics.actualTotalTime += node.path("Actual Total Time").asDouble(0);
        metrics.actualRows += node.path("Actual Rows").asLong(0);

        // Анализ по типам узлов
        switch (nodeType) {
            case "Seq Scan":
                metrics.seqScanCount++;
                long seqScanRows = node.path("Actual Rows").asLong(0);
                if (seqScanRows > metrics.maxSeqScanRows) {
                    metrics.maxSeqScanRows = seqScanRows;
                    metrics.largestSeqScanTable = node.path("Relation Name").asText();
                }
                break;

            case "Index Scan":
            case "Index Only Scan":
                metrics.indexScanCount++;
                break;

            case "Nested Loop":
            case "Hash Join":
            case "Merge Join":
                metrics.joinCount++;
                long joinRows = node.path("Actual Rows").asLong(0);
                if (joinRows > metrics.maxJoinRows) {
                    metrics.maxJoinRows = joinRows;
                }
                break;

            case "Sort":
                metrics.sortCount++;
                String sortSpace = node.path("Sort Space Type").asText();
                if ("Disk".equals(sortSpace)) {
                    metrics.diskSortCount++;
                }
                break;

            case "Aggregate":
            case "GroupAggregate":
                metrics.aggregateCount++;
                break;
        }

        // Анализ использования буферов
        JsonNode sharedHitBlocks = node.path("Shared Hit Blocks");
        JsonNode sharedReadBlocks = node.path("Shared Read Blocks");

        if (sharedHitBlocks.isNumber()) {
            metrics.sharedHitBlocks += sharedHitBlocks.asLong();
        }
        if (sharedReadBlocks.isNumber()) {
            metrics.sharedReadBlocks += sharedReadBlocks.asLong();
        }

        // Рекурсивный обход дочерних узлов
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                collectMetricsRecursive(child, metrics);
            }
        }
    }

    /**
     * Анализ операций сканирования таблиц.
     * Выявляет проблемы с Seq Scan и отсутствием индексов.
     */
    private static void analyzeScans(JsonNode node, List<Recommendation> recommendations, PlanMetrics metrics) {
        if (node == null) return;

        String nodeType = node.get("Node Type").asText();

        // Проверка Seq Scan с большим количеством строк
        if ("Seq Scan".equals(nodeType)) {
            long actualRows = node.path("Actual Rows").asLong(0);
            long rowsRemoved = node.path("Rows Removed by Filter").asLong(0);
            String tableName = node.path("Relation Name").asText();

            // Эвристика: если отфильтровано много строк, нужен индекс
            double filterRatio = actualRows > 0 ? (double) rowsRemoved / actualRows : 0;

            if (actualRows > SEQ_SCAN_THRESHOLD && filterRatio > 0.1) {
                addRecommendation(
                        recommendations,
                        RecommendationType.CREATE_INDEX,
                        String.format("Seq Scan на таблице '%s' обработал %,d строк, отфильтровано %,d (%.1f%%). " +
                                        "Рассмотрите создание индекса на условиях фильтрации.",
                                tableName, actualRows, rowsRemoved, filterRatio * 100),
                        filterRatio > 0.3 ? ImpactLevel.HIGH : ImpactLevel.MEDIUM,
                        Math.min(filterRatio * 100, 50.0)
                );
            }
        }

        // Проверка условий фильтрации без использования индекса
        if (node.has("Filter")) {
            String filter = node.get("Filter").asText();
            boolean isIndexed = nodeType.contains("Index");

            // Определяем потенциально индексируемые условия
            if (!isIndexed && (filter.contains("=") || filter.contains(">") || filter.contains("<") ||
                    filter.contains("LIKE") || filter.contains("IN"))) {

                // Анализируем сложность фильтра
                int conditionCount = countFilterConditions(filter);

                if (conditionCount > 0) {
                    addRecommendation(
                            recommendations,
                            RecommendationType.CREATE_INDEX,
                            String.format("Обнаружены условия фильтрации без использования индекса: %s. " +
                                            "Рассмотрите создание составного индекса.",
                                    abbreviateFilter(filter, 100)),
                            conditionCount > 2 ? ImpactLevel.HIGH : ImpactLevel.MEDIUM,
                            30.0
                    );
                }
            }
        }

        // Рекурсивный обход
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                analyzeScans(child, recommendations, metrics);
            }
        }
    }

    /**
     * Анализ операций соединения (JOIN).
     * Выявляет неоптимальные JOIN и предлагает улучшения.
     */
    private static void analyzeJoins(JsonNode node, List<Recommendation> recommendations, PlanMetrics metrics) {
        if (node == null) return;

        String nodeType = node.get("Node Type").asText();

        if ("Nested Loop".equals(nodeType) || "Hash Join".equals(nodeType) || "Merge Join".equals(nodeType)) {
            long actualRows = node.path("Actual Rows").asLong(0);
            double actualTime = node.path("Actual Total Time").asDouble(0);

            // Проверка Cartesian Join (отсутствие условия соединения)
            if (!node.has("Join Filter") && !node.has("Hash Cond") && !node.has("Merge Cond")) {
                addRecommendation(
                        recommendations,
                        RecommendationType.REWRITE_QUERY,
                        String.format("Обнаружен Cartesian Join (%,d строк, %.2f мс). " +
                                        "Добавьте условие соединения или используйте INNER/OUTER JOIN.",
                                actualRows, actualTime),
                        ImpactLevel.HIGH,
                        70.0
                );
            }

            // Проверка неоптимального типа JOIN
            if ("Nested Loop".equals(nodeType) && actualRows > 10000) {
                addRecommendation(
                        recommendations,
                        RecommendationType.CHANGE_JOIN_TYPE,
                        String.format("Nested Loop Join обрабатывает много строк (%,d). " +
                                        "Рассмотрите Hash Join для больших наборов данных.",
                                actualRows),
                        ImpactLevel.MEDIUM,
                        40.0
                );
            }

            // Проверка отсутствия индексов для JOIN
            if (node.has("Join Filter")) {
                String joinFilter = node.get("Join Filter").asText();
                if (joinFilter.contains("=") && !nodeType.contains("Index")) {
                    addRecommendation(
                            recommendations,
                            RecommendationType.CREATE_INDEX,
                            String.format("Условие соединения '%s' не использует индекс. " +
                                            "Создайте индексы на соединяемых столбцах.",
                                    abbreviateFilter(joinFilter, 80)),
                            ImpactLevel.HIGH,
                            50.0
                    );
                }
            }
        }

        // Рекурсивный обход
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                analyzeJoins(child, recommendations, metrics);
            }
        }
    }

    /**
     * Анализ операций агрегации.
     */
    private static void analyzeAggregations(JsonNode node, List<Recommendation> recommendations) {
        if (node == null) return;

        String nodeType = node.get("Node Type").asText();

        if ("Aggregate".equals(nodeType) || "GroupAggregate".equals(nodeType)) {
            String strategy = node.path("Strategy").asText();
            long actualRows = node.path("Actual Rows").asLong(0);
            double actualTime = node.path("Actual Total Time").asDouble(0);

            // Проверка агрегации большого количества строк
            if (actualRows > 100000 && actualTime > 1000) {
                addRecommendation(
                        recommendations,
                        RecommendationType.REWRITE_QUERY,
                        String.format("Агрегация %,d строк заняла %.2f мс. " +
                                        "Рассмотрите предварительную фильтрацию или материализацию промежуточных результатов.",
                                actualRows, actualTime),
                        actualTime > 5000 ? ImpactLevel.HIGH : ImpactLevel.MEDIUM,
                        35.0
                );
            }

            // Проверка агрегации без группировки
            if ("Plain".equals(strategy) && actualRows > 1000) {
                JsonNode parent = findParentNode(node, "Scan");
                if (parent != null) {
                    addRecommendation(
                            recommendations,
                            RecommendationType.ADD_LIMIT,
                            "Агрегация без группировки возвращает много строк. Добавьте LIMIT или более строгий фильтр.",
                            ImpactLevel.LOW,
                            15.0
                    );
                }
            }
        }

        // Рекурсивный обход
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                analyzeAggregations(child, recommendations);
            }
        }
    }

    /**
     * Анализ операций сортировки.
     */
    private static void analyzeSortOperations(JsonNode node, List<Recommendation> recommendations) {
        if (node == null) return;

        String nodeType = node.get("Node Type").asText();

        if ("Sort".equals(nodeType)) {
            String sortSpaceType = node.path("Sort Space Type").asText();
            String sortKey = node.path("Sort Key").asText();
            long actualRows = node.path("Actual Rows").asLong(0);
            double actualTime = node.path("Actual Total Time").asDouble(0);

            // Сортировка на диске
            if ("Disk".equals(sortSpaceType)) {
                addRecommendation(
                        recommendations,
                        RecommendationType.INCREASE_WORK_MEM,
                        String.format("Сортировка %,d строк использовала диск (%.2f мс). " +
                                        "Увеличьте work_mem или создайте индекс на %s.",
                                actualRows, actualTime,
                                sortKey.length() > 50 ? "полях сортировки" : sortKey),
                        actualRows > 10000 ? ImpactLevel.HIGH : ImpactLevel.MEDIUM,
                        45.0
                );
            }

            // Сортировка без использования индекса
            if (!sortKey.isEmpty() && actualRows > 5000) {
                addRecommendation(
                        recommendations,
                        RecommendationType.CREATE_INDEX,
                        String.format("Сортировка %,d строк по %s. Рассмотрите создание индекса для избежания сортировки.",
                                actualRows,
                                sortKey.length() > 60 ? sortKey.substring(0, 57) + "..." : sortKey),
                        actualRows > 50000 ? ImpactLevel.HIGH : ImpactLevel.MEDIUM,
                        40.0
                );
            }
        }

        // Рекурсивный обход
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                analyzeSortOperations(child, recommendations);
            }
        }
    }

    /**
     * Анализ использования памяти.
     */
    private static void analyzeMemoryUsage(JsonNode node, List<Recommendation> recommendations) {
        if (node == null) return;

        // Анализ использования буферов
        long sharedHitBlocks = node.path("Shared Hit Blocks").asLong(0);
        long sharedReadBlocks = node.path("Shared Read Blocks").asLong(0);

        if (sharedReadBlocks > HIGH_BUFFER_THRESHOLD) {
            String nodeType = node.get("Node Type").asText();
            String objectName = node.path("Relation Name").asText();

            if (objectName.isEmpty()) {
                objectName = nodeType;
            }

            double hitRatio = sharedHitBlocks + sharedReadBlocks > 0 ?
                    (double) sharedHitBlocks / (sharedHitBlocks + sharedReadBlocks) : 0;

            if (hitRatio < 0.8) { // Низкий коэффициент попадания в кэш
                addRecommendation(
                        recommendations,
                        RecommendationType.TUNE_DB_PARAMS,
                        String.format("Низкий коэффициент попадания в кэш (%.1f%%) для %s. " +
                                        "Увеличьте shared_buffers или рассмотрите кэширование результатов.",
                                hitRatio * 100, objectName),
                        sharedReadBlocks > 50000 ? ImpactLevel.HIGH : ImpactLevel.MEDIUM,
                        25.0
                );
            }
        }

        // Рекурсивный обход
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                analyzeMemoryUsage(child, recommendations);
            }
        }
    }

    /**
     * Анализ параллельного выполнения.
     */
    private static void analyzeParallelism(JsonNode node, List<Recommendation> recommendations) {
        if (node == null) return;

        String nodeType = node.get("Node Type").asText();

        // Проверка узлов, которые могут быть распараллелены
        if (("Seq Scan".equals(nodeType) || "Index Scan".equals(nodeType) ||
                "Hash Join".equals(nodeType) || "Aggregate".equals(nodeType)) &&
                !node.path("Workers").isMissingNode()) {

            JsonNode workers = node.get("Workers");
            int plannedWorkers = node.path("Workers Planned").asInt(0);
            int launchedWorkers = node.path("Workers Launched").asInt(0);

            // Проверка эффективности параллелизма
            if (plannedWorkers > 0 && launchedWorkers < plannedWorkers) {
                addRecommendation(
                        recommendations,
                        RecommendationType.TUNE_DB_PARAMS,
                        String.format("Параллельное выполнение: запланировано %d воркеров, запущено %d. " +
                                        "Настройте max_parallel_workers_per_gather.",
                                plannedWorkers, launchedWorkers),
                        ImpactLevel.MEDIUM,
                        20.0
                );
            }
        }

        // Проверка узлов, которые не используют параллелизм, но могли бы
        if (("Seq Scan".equals(nodeType) || "Hash Join".equals(nodeType)) &&
                node.path("Workers Planned").asInt(0) == 0) {

            long actualRows = node.path("Actual Rows").asLong(0);
            if (actualRows > 100000) {
                addRecommendation(
                        recommendations,
                        RecommendationType.ENABLE_PARALLEL,
                        String.format("Операция с %,d строками не использует параллелизм. " +
                                        "Рассмотрите настройку parallel_tuple_cost/parallel_setup_cost.",
                                actualRows),
                        ImpactLevel.MEDIUM,
                        30.0
                );
            }
        }

        // Рекурсивный обход
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                analyzeParallelism(child, recommendations);
            }
        }
    }

    /**
     * Анализ вложенных запросов.
     */
    private static void analyzeSubqueries(JsonNode node, List<Recommendation> recommendations) {
        if (node == null) return;

        // Эвристика: если есть Subquery Scan или Materialize с большим количеством строк
        String nodeType = node.get("Node Type").asText();

        if (("Subquery Scan".equals(nodeType) || "Materialize".equals(nodeType)) &&
                node.path("Actual Rows").asLong(0) > 10000) {

            addRecommendation(
                    recommendations,
                    RecommendationType.REWRITE_QUERY,
                    String.format("%s обрабатывает %,d строк. Рассмотрите переписывание вложенного запроса в JOIN.",
                            nodeType, node.path("Actual Rows").asLong()),
                    ImpactLevel.MEDIUM,
                    35.0
            );
        }

        // Рекурсивный обход
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                analyzeSubqueries(child, recommendations);
            }
        }
    }

    /**
     * Анализ использования временных таблиц.
     */
    private static void analyzeTempTables(JsonNode node, List<Recommendation> recommendations) {
        if (node == null) return;

        String nodeType = node.get("Node Type").asText();

        if ("Materialize".equals(nodeType) || "Hash".equals(nodeType)) {
            long actualRows = node.path("Actual Rows").asLong(0);

            if (actualRows > 50000) {
                addRecommendation(
                        recommendations,
                        RecommendationType.USE_TEMP_TABLE,
                        String.format("Материализация %,d строк во временную структуру. " +
                                        "Рассмотрите использование временной таблицы с индексами.",
                                actualRows),
                        ImpactLevel.MEDIUM,
                        25.0
                );
            }
        }

        // Рекурсивный обход
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                analyzeTempTables(child, recommendations);
            }
        }
    }

    /**
     * Общий анализ производительности плана.
     */
    private static void analyzeOverallPerformance(JsonNode node, List<Recommendation> recommendations, PlanMetrics metrics) {
        if (metrics.totalCost == 0) return;

        // Анализ соотношения Seq Scan vs Index Scan
        if (metrics.seqScanCount > 0 && metrics.indexScanCount == 0) {
            double seqScanRatio = (double) metrics.seqScanCount / (metrics.seqScanCount + metrics.indexScanCount);

            if (seqScanRatio > 0.7) { // Преобладание Seq Scan
                addRecommendation(
                        recommendations,
                        RecommendationType.UPDATE_STATISTICS,
                        String.format("Высокое соотношение Seq Scan (%.1f%%). Обновите статистику таблиц.",
                                seqScanRatio * 100),
                        ImpactLevel.MEDIUM,
                        20.0
                );
            }
        }

        // Анализ использования диска для сортировки
        if (metrics.sortCount > 0) {
            double diskSortRatio = (double) metrics.diskSortCount / metrics.sortCount;

            if (diskSortRatio > 0.3) { // Более 30% сортировок на диске
                addRecommendation(
                        recommendations,
                        RecommendationType.TUNE_DB_PARAMS,
                        String.format("Высокий процент сортировок на диске (%.1f%%). Настройте work_mem и maintenance_work_mem.",
                                diskSortRatio * 100),
                        ImpactLevel.HIGH,
                        40.0
                );
            }
        }

        // Анализ общего времени выполнения
        if (metrics.actualTotalTime > 5000) { // Более 5 секунд
            addRecommendation(
                    recommendations,
                    RecommendationType.SPLIT_QUERY,
                    String.format("Общее время выполнения %.2f мс. Рассмотрите разбиение запроса на части.",
                            metrics.actualTotalTime),
                    metrics.actualTotalTime > 30000 ? ImpactLevel.HIGH : ImpactLevel.MEDIUM,
                    30.0
            );
        }

        // Анализ большого количества JOIN
        if (metrics.joinCount > 5) {
            addRecommendation(
                    recommendations,
                    RecommendationType.REWRITE_QUERY,
                    String.format("Запрос содержит %d операций JOIN. Рассмотрите упрощение логики запроса.",
                            metrics.joinCount),
                    ImpactLevel.MEDIUM,
                    25.0
            );
        }
    }

    /**
     * Вспомогательный метод для добавления рекомендации.
     */
    private static void addRecommendation(List<Recommendation> recommendations,
                                          RecommendationType type,
                                          String description,
                                          ImpactLevel impact,
                                          Double estimatedImprovement) {
        Recommendation rec = new Recommendation();
        rec.setType(type);
        rec.setDescription(description);
        rec.setImpact(impact);
        rec.setEstimatedImprovement(estimatedImprovement);
        rec.setSqlSuggestion(generateSqlSuggestion(type, description));

        recommendations.add(rec);
    }

    /**
     * Генерация SQL-предложения на основе типа рекомендации.
     */
    private static String generateSqlSuggestion(RecommendationType type, String description) {
        switch (type) {
            case CREATE_INDEX:
                return "-- Пример создания индекса:\n-- CREATE INDEX idx_table_column ON table_name(column_name);";
            case REWRITE_QUERY:
                return "-- Перепишите запрос, используя оптимальные JOIN и фильтры";
            case ADD_LIMIT:
                return "-- Добавьте LIMIT для ограничения количества строк:\n-- SELECT ... LIMIT 100;";
            case INCREASE_WORK_MEM:
                return "-- Настройка work_mem в postgresql.conf:\n-- work_mem = 64MB";
            case TUNE_DB_PARAMS:
                return "-- Рекомендуемые параметры:\n-- shared_buffers = 25% от RAM\n-- effective_cache_size = 75% от RAM";
            default:
                return "См. документацию по оптимизации PostgreSQL";
        }
    }

    /**
     * Подсчет условий в фильтре.
     */
    private static int countFilterConditions(String filter) {
        if (filter == null || filter.isEmpty()) return 0;

        // Простая эвристика: считаем операторы сравнения
        String[] operators = {"=", ">", "<", ">=", "<=", "<>", "!=", "LIKE", "IN", "BETWEEN"};
        int count = 0;

        for (String op : operators) {
            int index = filter.indexOf(op);
            while (index != -1) {
                count++;
                index = filter.indexOf(op, index + 1);
            }
        }

        return count;
    }

    /**
     * Сокращение длинного фильтра для отображения.
     */
    private static String abbreviateFilter(String filter, int maxLength) {
        if (filter.length() <= maxLength) return filter;
        return filter.substring(0, maxLength - 3) + "...";
    }

    /**
     * Поиск родительского узла по типу.
     */
    private static JsonNode findParentNode(JsonNode node, String parentTypeContains) {
        // Упрощенная реализация - в реальной системе нужно хранить связи между узлами
        return null;
    }

    /**
     * Удаление дубликатов рекомендаций.
     */
    private static List<Recommendation> deduplicateRecommendations(List<Recommendation> recommendations) {
        Map<String, Recommendation> uniqueRecs = new LinkedHashMap<>();

        for (Recommendation rec : recommendations) {
            String key = rec.getType() + ":" + rec.getDescription().substring(0, Math.min(50, rec.getDescription().length()));
            uniqueRecs.putIfAbsent(key, rec);
        }

        return new ArrayList<>(uniqueRecs.values());
    }

    /**
     * Внутренний класс для сбора метрик плана.
     */
    private static class PlanMetrics {
        long seqScanCount = 0;
        long indexScanCount = 0;
        long joinCount = 0;
        long sortCount = 0;
        long diskSortCount = 0;
        long aggregateCount = 0;
        long actualRows = 0;
        long maxSeqScanRows = 0;
        long maxJoinRows = 0;
        long sharedHitBlocks = 0;
        long sharedReadBlocks = 0;
        double totalCost = 0;
        double actualTotalTime = 0;
        String largestSeqScanTable = "";
    }
}