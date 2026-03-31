package com.pgoptima.analyticsservice.service.impl;

import com.pgoptima.analyticsservice.client.UserServiceClient;
import com.pgoptima.analyticsservice.config.PlanAnalyzerProperties;
import com.pgoptima.analyticsservice.dto.ConnectionDetails;
import com.pgoptima.analyticsservice.entity.OptimizationHistory;
import com.pgoptima.analyticsservice.entity.RecommendationResult;
import com.pgoptima.analyticsservice.repository.OptimizationHistoryRepository;
import com.pgoptima.analyticsservice.repository.RecommendationResultRepository;
import com.pgoptima.analyticsservice.service.AnalyticsService;
import com.pgoptima.analyticsservice.service.RecommendationApplier;
import com.pgoptima.analyticsservice.util.PlanAnalyzer;
import com.pgoptima.analyticsservice.util.PostgrePlanParser;
import com.pgoptima.analyticsservice.util.SqlQueryTypeDetector;
import com.fasterxml.jackson.databind.JsonNode;
import com.pgoptima.shareddto.request.AnalysisRequest;
import com.pgoptima.shareddto.response.AnalysisResponse;
import com.pgoptima.shareddto.response.Recommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostgreAnalyticsServiceImpl implements AnalyticsService {

    private final UserServiceClient userServiceClient;
    private final PlanAnalyzer planAnalyzer;
    private final RecommendationApplier recommendationApplier;
    private final OptimizationHistoryRepository historyRepository;
    private final RecommendationResultRepository resultRepository;
    private final PlanAnalyzerProperties properties;

    @Override
    @Cacheable(value = "analysisResults", key = "#request.connectionId + '-' + #request.sqlQuery.hashCode()", unless = "#result.success == false")
    public AnalysisResponse analyzeQuery(AnalysisRequest request) {
        Instant start = Instant.now();
        AnalysisResponse response = AnalysisResponse.builder()
                .originalQuery(request.getSqlQuery())
                .requestTimestamp(Instant.now())
                .success(false)
                .build();

        if (SqlQueryTypeDetector.isModifyingQuery(request.getSqlQuery()) && !properties.isAllowModifyingQueries()) {
            response.setErrorMessage("Modifying queries (INSERT/UPDATE/DELETE) are not allowed for analysis.");
            return response;
        }

        ConnectionDetails connection;
        try {
            connection = userServiceClient.getConnectionById(request.getConnectionId(), "Bearer dummy");
        } catch (Exception e) {
            response.setErrorMessage("Failed to fetch connection details: " + e.getMessage());
            return response;
        }

        PlanExecutionResult execResult;
        try {
            execResult = executeExplain(request.getSqlQuery(), connection, request.isAutoApply());
        } catch (SQLException e) {
            response.setErrorMessage("Database error: " + e.getMessage());
            return response;
        }

        response.setExecutionPlanJson(execResult.planJson);
        response.setOriginalExecutionTime(execResult.executionTime);

        List<Recommendation> recommendations;
        try {
            JsonNode plan = PostgrePlanParser.parse(execResult.planJson);
            recommendations = planAnalyzer.analyze(plan, request.getSqlQuery());
        } catch (Exception e) {
            response.setErrorMessage("Plan analysis failed: " + e.getMessage());
            return response;
        }

        if (request.isAutoApply() && properties.isAllowModifyingQueries()) {
            recommendations = recommendationApplier.applyAndMeasure(recommendations,
                    request.getSqlQuery(), connection, execResult.executionTime);
            calculateStats(response, recommendations);
        }

        response.setRecommendations(recommendations);
        response.setSuccess(true);
        generateReport(response);
        response.setAnalysisDuration(Duration.between(start, Instant.now()));

        saveHistory(request, response, connection);

        return response;
    }

    private PlanExecutionResult executeExplain(String sql, ConnectionDetails conn, boolean autoApply) throws SQLException {
        String url = String.format("jdbc:postgresql://%s:%d/%s?sslmode=%s",
                conn.getHost(), conn.getPort(), conn.getDatabase(), conn.getSslMode());
        try (Connection connection = DriverManager.getConnection(url, conn.getUsername(), conn.getPassword())) {
            String explainSql = "EXPLAIN (ANALYZE, BUFFERS, VERBOSE, FORMAT JSON) " + sql;
            Instant queryStart = Instant.now();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(explainSql)) {
                String planJson = rs.next() ? rs.getString(1) : null;
                Duration execTime = Duration.between(queryStart, Instant.now());
                return new PlanExecutionResult(planJson, execTime);
            }
        }
    }

    private void calculateStats(AnalysisResponse response, List<Recommendation> recommendations) {
        long applied = recommendations.stream().filter(Recommendation::getApplied).count();
        OptionalDouble avg = recommendations.stream()
                .filter(r -> r.getActualImprovement() != null && r.getActualImprovement() > 0)
                .mapToDouble(Recommendation::getActualImprovement)
                .average();
        Map<String, Object> stats = new HashMap<>();
        stats.put("applied_recommendations", applied);
        stats.put("total_recommendations", recommendations.size());
        stats.put("average_improvement", avg.orElse(0.0));
        response.setOptimizationStatistics(stats);
    }

    private void generateReport(AnalysisResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Optimization Report ===\n");
        sb.append("Original query: ").append(response.getOriginalQuery()).append("\n");
        sb.append("Original time: ").append(response.getOriginalExecutionTime().toMillis()).append(" ms\n");
        if (response.getOptimizationStatistics() != null) {
            sb.append("Applied: ").append(response.getOptimizationStatistics().get("applied_recommendations"))
                    .append(" / ").append(response.getOptimizationStatistics().get("total_recommendations")).append("\n");
        }
        for (Recommendation rec : response.getRecommendations()) {
            sb.append("- ").append(rec.getDescription()).append("\n");
        }
        response.setOptimizationReport(sb.toString());
    }

    @Transactional
    protected void saveHistory(AnalysisRequest request, AnalysisResponse response, ConnectionDetails conn) {
        OptimizationHistory history = new OptimizationHistory();
        history.setUserId(1L);
        history.setConnectionId(request.getConnectionId());
        history.setOriginalQuery(request.getSqlQuery());
        history.setOriginalExecutionTime(response.getOriginalExecutionTime());
        history.setSuccess(response.isSuccess());
        if (response.getOptimizationStatistics() != null && response.getOptimizationStatistics().containsKey("average_improvement")) {
            history.setImprovementPercent((Double) response.getOptimizationStatistics().get("average_improvement"));
        }
        history = historyRepository.save(history);

        for (Recommendation rec : response.getRecommendations()) {
            RecommendationResult res = new RecommendationResult();
            res.setHistoryId(history.getId());
            res.setType(rec.getType());
            res.setDescription(rec.getDescription());
            res.setSqlCommand(rec.getSqlCommand());
            res.setApplied(rec.getApplied());
            res.setEstimatedImprovement(rec.getEstimatedImprovement());
            res.setActualImprovement(rec.getActualImprovement());
            resultRepository.save(res);
        }
    }

    private static class PlanExecutionResult {
        String planJson;
        Duration executionTime;
        PlanExecutionResult(String planJson, Duration executionTime) {
            this.planJson = planJson;
            this.executionTime = executionTime;
        }
    }
}