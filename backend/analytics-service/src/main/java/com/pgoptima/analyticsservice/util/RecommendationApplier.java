package com.pgoptima.analyticsservice.service;

import com.pgoptima.shareddto.enums.RecommendationType;
import com.pgoptima.shareddto.response.Recommendation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
public class RecommendationApplier {

    private static final int MAX_APPLICATION_TIME_SECONDS = 30;
    private static final int QUERY_TIMEOUT_SECONDS = 10;
    private static final ExecutorService executorService = Executors.newFixedThreadPool(3);

    /**
     * Применяет рекомендации и измеряет улучшения
     */
    public List<Recommendation> applyAndMeasure(
            List<Recommendation> recommendations,
            String originalQuery,
            ConnectionParams connectionParams,
            Duration originalExecutionTime) {

        List<Recommendation> measuredRecommendations = new ArrayList<>();

        for (Recommendation recommendation : recommendations) {
            try {
                Recommendation measured = applySingleRecommendation(
                        recommendation,
                        originalQuery,
                        connectionParams,
                        originalExecutionTime
                );
                measuredRecommendations.add(measured);
            } catch (Exception e) {
                log.warn("Не удалось применить рекомендацию: {}", recommendation.getDescription(), e);
                recommendation.getWarnings().add("Ошибка применения: " + e.getMessage());
                measuredRecommendations.add(recommendation);
            }
        }

        return measuredRecommendations;
    }

    /**
     * Применяет одну рекомендацию и измеряет результат
     */
    private Recommendation applySingleRecommendation(
            Recommendation recommendation,
            String originalQuery,
            ConnectionParams connectionParams,
            Duration originalExecutionTime) throws Exception {

        // Сохраняем исходное время
        recommendation.setOriginalExecutionTime(originalExecutionTime);

        // Пропускаем рекомендации без SQL команд или те, которые нельзя применить автоматически
        if (recommendation.getSqlCommand() == null ||
                !isAutoApplicable(recommendation.getType())) {
            recommendation.getWarnings().add("Рекомендация не может быть применена автоматически");
            return recommendation;
        }

        // Подготавливаем контекст выполнения
        ExecutionContext context = prepareExecutionContext(
                recommendation,
                connectionParams
        );

        try {
            // Выполняем в отдельном потоке с таймаутом
            CompletableFuture<ExecutionResult> future = CompletableFuture.supplyAsync(
                    () -> executeWithOptimization(context, originalQuery),
                    executorService
            );

            ExecutionResult result = future.get(MAX_APPLICATION_TIME_SECONDS, TimeUnit.SECONDS);

            // Обновляем рекомендацию с результатами
            return updateRecommendationWithResult(recommendation, result);

        } catch (TimeoutException e) {
            recommendation.getWarnings().add("Превышено время применения рекомендации");
            recommendation.setApplied(false);
        } catch (Exception e) {
            recommendation.getWarnings().add("Ошибка выполнения: " + e.getMessage());
            recommendation.setApplied(false);
        } finally {
            // Очищаем временные объекты
            cleanup(context);
        }

        return recommendation;
    }

    /**
     * Проверяет, можно ли автоматически применить рекомендацию
     */
    private boolean isAutoApplicable(RecommendationType type) {
        switch (type) {
            case CREATE_INDEX:
            case ADD_LIMIT:
            case USE_TEMP_TABLE:
                return true;
            case REWRITE_QUERY:
            case CHANGE_JOIN_TYPE:
            case INCREASE_WORK_MEM:
            case TUNE_DB_PARAMS:
            case ENABLE_PARALLEL:
            case UPDATE_STATISTICS:
            case SPLIT_QUERY:
            case OTHER:
            default:
                return false;
        }
    }

    /**
     * Подготавливает контекст выполнения
     */
    private ExecutionContext prepareExecutionContext(
            Recommendation recommendation,
            ConnectionParams connectionParams) throws SQLException {

        ExecutionContext context = new ExecutionContext();
        context.connectionParams = connectionParams;
        context.recommendation = recommendation;

        // Создаем основное соединение
        context.mainConnection = createConnection(connectionParams);
        context.mainConnection.setAutoCommit(false);

        // Создаем тестовое соединение для измерений
        context.testConnection = createConnection(connectionParams);
        context.testConnection.setAutoCommit(false);

        return context;
    }

    /**
     * Выполняет оптимизацию и измеряет результат
     */
    private ExecutionResult executeWithOptimization(
            ExecutionContext context,
            String originalQuery) {

        ExecutionResult result = new ExecutionResult();
        result.recommendationType = context.recommendation.getType();

        try {
            // Шаг 1: Выполняем исходный запрос для получения базового времени
            Duration baselineTime = executeQueryWithTiming(
                    context.testConnection,
                    originalQuery
            );
            result.baselineTime = baselineTime;

            // Шаг 2: Применяем рекомендацию
            applyRecommendation(context.mainConnection, context.recommendation);

            // Шаг 3: Выполняем запрос после оптимизации
            Duration optimizedTime = executeQueryWithTiming(
                    context.testConnection,
                    originalQuery
            );
            result.optimizedTime = optimizedTime;

            // Шаг 4: Рассчитываем улучшение
            result.improvementPercent = calculateImprovement(
                    baselineTime,
                    optimizedTime
            );
            result.success = true;

            // Шаг 5: Собираем дополнительные метрики
            result.metrics = collectAdditionalMetrics(
                    context.testConnection,
                    originalQuery
            );

        } catch (Exception e) {
            result.success = false;
            result.errorMessage = e.getMessage();
            log.error("Ошибка выполнения оптимизации", e);
        }

        return result;
    }

    /**
     * Применяет рекомендацию в базе данных
     */
    private void applyRecommendation(Connection connection, Recommendation recommendation)
            throws SQLException {

        try (Statement stmt = connection.createStatement()) {
            switch (recommendation.getType()) {
                case CREATE_INDEX:
                    applyCreateIndex(stmt, recommendation);
                    break;
                case ADD_LIMIT:
                    // Для ADD_LIMIT модифицируем запрос вместо применения отдельной команды
                    break;
                case USE_TEMP_TABLE:
                    applyTempTable(stmt, recommendation);
                    break;
                default:
                    throw new UnsupportedOperationException(
                            "Тип рекомендации не поддерживается для автоматического применения: " +
                                    recommendation.getType()
                    );
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        }
    }

    /**
     * Применяет создание индекса
     */
    private void applyCreateIndex(Statement stmt, Recommendation recommendation)
            throws SQLException {

        String sqlCommand = recommendation.getSqlCommand();
        if (sqlCommand == null || sqlCommand.isEmpty()) {
            throw new IllegalArgumentException("SQL команда для создания индекса отсутствует");
        }

        // Проверяем, не существует ли уже индекс
        if (!indexExists(stmt, recommendation)) {
            log.info("Создаем индекс: {}", sqlCommand);
            stmt.execute(sqlCommand);
        } else {
            log.info("Индекс уже существует, пропускаем создание");
            recommendation.getWarnings().add("Индекс уже существует в базе данных");
        }
    }

    /**
     * Проверяет существование индекса
     */
    private boolean indexExists(Statement stmt, Recommendation recommendation)
            throws SQLException {

        // Извлекаем имя индекса из SQL команды
        String sqlCommand = recommendation.getSqlCommand();
        String indexName = extractIndexName(sqlCommand);

        if (indexName == null) return false;

        // Запрос для проверки существования индекса
        String checkQuery = String.format(
                "SELECT 1 FROM pg_indexes WHERE indexname = '%s'",
                indexName
        );

        try (ResultSet rs = stmt.executeQuery(checkQuery)) {
            return rs.next();
        }
    }

    /**
     * Извлекает имя индекса из SQL команды
     */
    private String extractIndexName(String sqlCommand) {
        if (sqlCommand == null) return null;

        // Паттерн: CREATE INDEX index_name ON ...
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "CREATE\\s+INDEX\\s+(\\w+)\\s+ON",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );

        java.util.regex.Matcher matcher = pattern.matcher(sqlCommand);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Применяет создание временной таблицы
     */
    private void applyTempTable(Statement stmt, Recommendation recommendation)
            throws SQLException {

        // Для временных таблиц нужно анализировать SQL команду
        String sqlCommand = recommendation.getSqlCommand();
        if (sqlCommand == null) return;

        // Разделяем команды по точкам с запятой
        String[] commands = sqlCommand.split(";");
        for (String cmd : commands) {
            cmd = cmd.trim();
            if (!cmd.isEmpty()) {
                if (cmd.toUpperCase().startsWith("CREATE TEMP TABLE") ||
                        cmd.toUpperCase().startsWith("CREATE TEMPORARY TABLE")) {
                    log.info("Создаем временную таблицу: {}", cmd);
                    stmt.execute(cmd);
                }
            }
        }
    }

    /**
     * Выполняет запрос с измерением времени
     */
    private Duration executeQueryWithTiming(Connection connection, String query)
            throws SQLException {

        long startTime = System.nanoTime();

        try (Statement stmt = connection.createStatement()) {
            stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

            // Используем EXPLAIN ANALYZE для измерения без изменения данных
            String explainQuery = "EXPLAIN (ANALYZE, BUFFERS, TIMING) " + query;

            try (ResultSet rs = stmt.executeQuery(explainQuery)) {
                // Читаем все результаты для точного измерения
                while (rs.next()) {
                    // Просто читаем данные
                }
            }

            connection.commit();
        }

        long endTime = System.nanoTime();
        return Duration.ofNanos(endTime - startTime);
    }

    /**
     * Рассчитывает процент улучшения
     */
    private double calculateImprovement(Duration baseline, Duration optimized) {
        if (baseline.isZero()) return 0.0;

        double baselineMs = baseline.toMillis();
        double optimizedMs = optimized.toMillis();

        if (baselineMs <= optimizedMs) {
            return 0.0; // Нет улучшения или стало хуже
        }

        return ((baselineMs - optimizedMs) / baselineMs) * 100.0;
    }

    /**
     * Собирает дополнительные метрики
     */
    private Map<String, Object> collectAdditionalMetrics(
            Connection connection,
            String query) throws SQLException {

        Map<String, Object> metrics = new HashMap<>();

        try (Statement stmt = connection.createStatement()) {
            // Получаем план выполнения с детальной информацией
            String explainQuery = "EXPLAIN (ANALYZE, BUFFERS, VERBOSE, FORMAT JSON) " + query;

            try (ResultSet rs = stmt.executeQuery(explainQuery)) {
                if (rs.next()) {
                    String planJson = rs.getString(1);
                    metrics.put("execution_plan_json", planJson);

                    // Парсим JSON для извлечения метрик
                    Map<String, Object> planMetrics = parsePlanMetrics(planJson);
                    metrics.putAll(planMetrics);
                }
            }
        }

        return metrics;
    }

    /**
     * Парсит метрики из плана выполнения
     */
    private Map<String, Object> parsePlanMetrics(String planJson) {
        Map<String, Object> metrics = new HashMap<>();

        try {
            // Простой парсинг JSON для извлечения ключевых метрик
            // В реальной системе используйте Jackson или другой JSON парсер
            if (planJson.contains("\"Execution Time\":")) {
                // Извлекаем время выполнения
                String timeStr = extractJsonValue(planJson, "Execution Time");
                if (timeStr != null) {
                    metrics.put("execution_time_ms", Double.parseDouble(timeStr));
                }
            }

            if (planJson.contains("\"Planning Time\":")) {
                String timeStr = extractJsonValue(planJson, "Planning Time");
                if (timeStr != null) {
                    metrics.put("planning_time_ms", Double.parseDouble(timeStr));
                }
            }

        } catch (Exception e) {
            log.warn("Не удалось распарсить метрики из плана выполнения", e);
        }

        return metrics;
    }

    /**
     * Извлекает значение из JSON строки
     */
    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\":\\s*([0-9.]+)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);

        if (m.find()) {
            return m.group(1);
        }

        return null;
    }

    /**
     * Обновляет рекомендацию с результатами выполнения
     */
    private Recommendation updateRecommendationWithResult(
            Recommendation recommendation,
            ExecutionResult result) {

        recommendation.setApplied(result.success);
        recommendation.setOptimizedExecutionTime(result.optimizedTime);
        recommendation.setActualImprovement(result.improvementPercent);

        if (result.metrics != null) {
            recommendation.getMetrics().putAll(result.metrics);
        }

        // Добавляем информацию о результате
        if (result.success) {
            if (result.improvementPercent > 0) {
                recommendation.getWarnings().add(String.format(
                        "Улучшение производительности: %.1f%%",
                        result.improvementPercent
                ));
            } else {
                recommendation.getWarnings().add("Улучшение не обнаружено");
            }
        } else if (result.errorMessage != null) {
            recommendation.getWarnings().add("Ошибка: " + result.errorMessage);
        }

        return recommendation;
    }

    /**
     * Очищает ресурсы
     */
    private void cleanup(ExecutionContext context) {
        try {
            if (context.mainConnection != null && !context.mainConnection.isClosed()) {
                // Откатываем транзакцию для удаления временных объектов
                context.mainConnection.rollback();
                context.mainConnection.close();
            }
        } catch (SQLException e) {
            log.warn("Ошибка при закрытии основного соединения", e);
        }

        try {
            if (context.testConnection != null && !context.testConnection.isClosed()) {
                context.testConnection.rollback();
                context.testConnection.close();
            }
        } catch (SQLException e) {
            log.warn("Ошибка при закрытии тестового соединения", e);
        }
    }

    /**
     * Создает соединение с базой данных
     */
    private Connection createConnection(ConnectionParams params) throws SQLException {
        String url = String.format("jdbc:postgresql://%s:%d/%s",
                params.host,
                params.port,
                params.database);

        return DriverManager.getConnection(url, params.username, params.password);
    }

    /**
     * Внутренние классы для хранения контекста
     */
    private static class ExecutionContext {
        ConnectionParams connectionParams;
        Recommendation recommendation;
        Connection mainConnection;
        Connection testConnection;
    }

    private static class ExecutionResult {
        boolean success;
        String errorMessage;
        Duration baselineTime;
        Duration optimizedTime;
        double improvementPercent;
        RecommendationType recommendationType;
        Map<String, Object> metrics = new HashMap<>();
    }

    public static class ConnectionParams {
        String host;
        int port;
        String database;
        String username;
        String password;

        public ConnectionParams(String host, int port, String database, String username, String password) {
            this.host = host;
            this.port = port;
            this.database = database;
            this.username = username;
            this.password = password;
        }
    }
}