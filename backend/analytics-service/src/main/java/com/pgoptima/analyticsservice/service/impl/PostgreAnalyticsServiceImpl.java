package com.pgoptima.analyticsservice.service.impl;

import com.pgoptima.analyticsservice.service.AnalyticsService;
import com.pgoptima.analyticsservice.service.RecommendationApplier;
import com.pgoptima.analyticsservice.util.PostgrePlanParser;
import com.pgoptima.analyticsservice.util.PlanAnalyzer;
import com.fasterxml.jackson.databind.JsonNode;
import com.pgoptima.shareddto.request.AnalysisRequest;
import com.pgoptima.shareddto.response.AnalysisResponse;
import com.pgoptima.shareddto.response.Recommendation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
public class PostgreAnalyticsServiceImpl implements AnalyticsService {

    @Autowired
    private RecommendationApplier recommendationApplier;

    @Value("${pgoptima.analysis.auto-apply:false}")
    private boolean autoApplyRecommendations;

    @Value("${pgoptima.analysis.max-apply-time-sec:30}")
    private int maxApplyTimeSeconds;

    @Override
    public AnalysisResponse analyzeQuery(AnalysisRequest request) {
        Instant start = Instant.now();
        AnalysisResponse response = new AnalysisResponse();
        response.setOriginalQuery(request.getSqlQuery());
        response.setRequestTimestamp(Instant.now());

        Connection connection = null;

        try {
            // 1. Получаем план выполнения и измеряем время
            PlanExecutionResult initialResult = executeExplainAnalyze(request);
            response.setExecutionPlanJson(initialResult.planJson);
            response.setOriginalExecutionTime(initialResult.executionTime);

            // 2. Парсим и анализируем план
            JsonNode plan = PostgrePlanParser.parse(initialResult.planJson);
            List<Recommendation> recommendations = PlanAnalyzer.analyze(plan, request.getSqlQuery());

            // 3. Если включено авто-применение, применяем рекомендации и измеряем улучшения
            if (autoApplyRecommendations && request.isAutoApply()) {
                log.info("Автоматическое применение рекомендаций включено");

                RecommendationApplier.ConnectionParams connectionParams =
                        new RecommendationApplier.ConnectionParams(
                                request.getConnection().getHost(),
                                request.getConnection().getPort(),
                                request.getConnection().getDatabase(),
                                request.getConnection().getUsername(),
                                request.getConnection().getPassword()
                        );

                recommendations = recommendationApplier.applyAndMeasure(
                        recommendations,
                        request.getSqlQuery(),
                        connectionParams,
                        initialResult.executionTime
                );

                // Сортируем по фактическому улучшению
                recommendations.sort((r1, r2) ->
                        Double.compare(r2.getActualImprovement(), r1.getActualImprovement())
                );

                // Рассчитываем общую статистику
                calculateOptimizationStatistics(response, recommendations);
            }

            response.setRecommendations(recommendations);
            response.setSuccess(true);

            // 4. Генерируем итоговый отчет
            generateOptimizationReport(response);

        } catch (SQLException e) {
            log.error("SQL error during analysis", e);
            response.setSuccess(false);
            response.setErrorMessage("Database error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Analysis failed", e);
            response.setSuccess(false);
            response.setErrorMessage("Analysis failed: " + e.getMessage());
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    log.warn("Error closing connection", e);
                }
            }
        }

        response.setAnalysisDuration(Duration.between(start, Instant.now()));
        return response;
    }

    /**
     * Выполняет EXPLAIN ANALYZE и возвращает результат
     */
    private PlanExecutionResult executeExplainAnalyze(AnalysisRequest request) throws SQLException {
        String url = String.format("jdbc:postgresql://%s:%d/%s",
                request.getConnection().getHost(),
                request.getConnection().getPort(),
                request.getConnection().getDatabase());

        try (Connection conn = DriverManager.getConnection(
                url,
                request.getConnection().getUsername(),
                request.getConnection().getPassword())) {

            String explainQuery = "EXPLAIN (ANALYZE, BUFFERS, VERBOSE, FORMAT JSON) " + request.getSqlQuery();

            Instant queryStart = Instant.now();

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(explainQuery)) {

                String planJson = null;
                if (rs.next()) {
                    planJson = rs.getString(1);
                }

                Duration executionTime = Duration.between(queryStart, Instant.now());

                return new PlanExecutionResult(planJson, executionTime);
            }
        }
    }

    /**
     * Рассчитывает статистику оптимизации
     */
    private void calculateOptimizationStatistics(AnalysisResponse response, List<Recommendation> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) return;

        long appliedCount = recommendations.stream()
                .filter(Recommendation::getApplied)
                .count();

        OptionalDouble avgImprovement = recommendations.stream()
                .filter(r -> r.getActualImprovement() != null && r.getActualImprovement() > 0)
                .mapToDouble(Recommendation::getActualImprovement)
                .average();

        OptionalDouble maxImprovement = recommendations.stream()
                .filter(r -> r.getActualImprovement() != null)
                .mapToDouble(Recommendation::getActualImprovement)
                .max();

        Map<String, Object> stats = new HashMap<>();
        stats.put("applied_recommendations", appliedCount);
        stats.put("total_recommendations", recommendations.size());
        stats.put("average_improvement", avgImprovement.orElse(0.0));
        stats.put("max_improvement", maxImprovement.orElse(0.0));

        // Лучшая рекомендация
        recommendations.stream()
                .filter(r -> r.getActualImprovement() != null && r.getActualImprovement() > 0)
                .max((r1, r2) -> Double.compare(r1.getActualImprovement(), r2.getActualImprovement()))
                .ifPresent(best -> {
                    stats.put("best_recommendation_type", best.getType());
                    stats.put("best_improvement", best.getActualImprovement());
                    stats.put("best_sql_command", best.getSqlCommand());
                });

        response.setOptimizationStatistics(stats);
    }

    /**
     * Генерирует отчет об оптимизации
     */
    private void generateOptimizationReport(AnalysisResponse response) {
        if (response.getRecommendations() == null || response.getRecommendations().isEmpty()) {
            return;
        }

        StringBuilder report = new StringBuilder();
        report.append("=== ОТЧЕТ ОБ ОПТИМИЗАЦИИ ЗАПРОСА ===\n\n");
        report.append("Исходный запрос: ").append(response.getOriginalQuery()).append("\n\n");
        report.append("Исходное время выполнения: ")
                .append(formatDuration(response.getOriginalExecutionTime()))
                .append("\n\n");

        List<Recommendation> recommendations = response.getRecommendations();
        Map<String, Object> stats = response.getOptimizationStatistics();

        if (stats != null && !stats.isEmpty()) {
            report.append("=== СТАТИСТИКА ОПТИМИЗАЦИИ ===\n");
            report.append("Применено рекомендаций: ").append(stats.get("applied_recommendations"))
                    .append(" из ").append(stats.get("total_recommendations")).append("\n");
            report.append("Среднее улучшение: ").append(String.format("%.1f%%", stats.get("average_improvement")))
                    .append("\n");
            report.append("Максимальное улучшение: ").append(String.format("%.1f%%", stats.get("max_improvement")))
                    .append("\n\n");

            if (stats.containsKey("best_recommendation_type")) {
                report.append("=== ЛУЧШАЯ РЕКОМЕНДАЦИЯ ===\n");
                report.append("Тип: ").append(stats.get("best_recommendation_type")).append("\n");
                report.append("Улучшение: ").append(String.format("%.1f%%", stats.get("best_improvement")))
                        .append("\n");
                report.append("SQL команда: ").append(stats.get("best_sql_command")).append("\n\n");
            }
        }

        report.append("=== ДЕТАЛЬНЫЕ РЕКОМЕНДАЦИИ ===\n\n");

        int counter = 1;
        for (Recommendation rec : recommendations) {
            report.append(counter++).append(". ").append(rec.getDescription()).append("\n");
            report.append("   Тип: ").append(rec.getType()).append("\n");
            report.append("   Влияние: ").append(rec.getImpact()).append("\n");

            if (rec.getSqlCommand() != null) {
                report.append("   SQL команда: ").append(rec.getSqlCommand()).append("\n");
            }

            if (rec.getApplied() != null && rec.getApplied()) {
                report.append("   Статус: Применена\n");
                if (rec.getActualImprovement() != null && rec.getActualImprovement() > 0) {
                    report.append("   Фактическое улучшение: ")
                            .append(String.format("%.1f%%", rec.getActualImprovement()))
                            .append("\n");
                }
            } else if (rec.getSqlCommand() != null) {
                report.append("   Статус: Требуется ручное применение\n");
            }

            if (!rec.getWarnings().isEmpty()) {
                report.append("   Предупреждения:\n");
                for (String warning : rec.getWarnings()) {
                    report.append("     - ").append(warning).append("\n");
                }
            }

            report.append("\n");
        }

        report.append("=== ИТОГИ ===\n");
        if (stats != null && (Double) stats.get("max_improvement") > 0) {
            report.append(String.format(
                    "Оптимизация может ускорить запрос до %.1f%%\n",
                    stats.get("max_improvement")
            ));
            report.append("Рекомендуется применить указанные изменения.\n");
        } else {
            report.append("Значительных улучшений не обнаружено.\n");
            report.append("Рассмотрите перепроектирование запроса или схемы базы данных.\n");
        }

        response.setOptimizationReport(report.toString());
    }

    /**
     * Форматирует продолжительность для отображения
     */
    private String formatDuration(Duration duration) {
        if (duration == null) return "N/A";

        if (duration.toMillis() < 1000) {
            return String.format("%d мс", duration.toMillis());
        } else if (duration.toSeconds() < 60) {
            return String.format("%.2f сек", duration.toMillis() / 1000.0);
        } else {
            long minutes = duration.toMinutes();
            long seconds = duration.minusMinutes(minutes).getSeconds();
            return String.format("%d мин %d сек", minutes, seconds);
        }
    }

    /**
     * Внутренний класс для хранения результата выполнения
     */
    private static class PlanExecutionResult {
        String planJson;
        Duration executionTime;

        PlanExecutionResult(String planJson, Duration executionTime) {
            this.planJson = planJson;
            this.executionTime = executionTime;
        }
    }
}