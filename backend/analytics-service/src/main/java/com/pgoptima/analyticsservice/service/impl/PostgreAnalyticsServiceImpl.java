package com.pgoptima.analyticsservice.service.impl;

import com.pgoptima.analyticsservice.service.AnalyticsService;
import com.pgoptima.analyticsservice.util.PostgrePlanParser;
import com.pgoptima.analyticsservice.util.PlanAnalyzer;
import com.fasterxml.jackson.databind.JsonNode;
import com.pgoptima.shareddto.request.AnalysisRequest;
import com.pgoptima.shareddto.response.AnalysisResponse;
import com.pgoptima.shareddto.response.Recommendation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
public class PostgreAnalyticsServiceImpl implements AnalyticsService {

    @Override
    public AnalysisResponse analyzeQuery(AnalysisRequest request) {
        Instant start = Instant.now();
        AnalysisResponse response = new AnalysisResponse();
        response.setOriginalQuery(request.getSqlQuery());

        try {
            // 1. Получаем план выполнения
            String planJson = executeExplainAnalyze(request);
            response.setExecutionPlanJson(planJson);

            // 2. Парсим и анализируем план
            JsonNode plan = PostgrePlanParser.parse(planJson);
            List<Recommendation> recommendations = PlanAnalyzer.analyze(plan);
            response.setRecommendations(recommendations);

            response.setSuccess(true);
        } catch (SQLException e) {
            log.error("SQL error during analysis", e);
            response.setSuccess(false);
            response.setErrorMessage("Database error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Analysis failed", e);
            response.setSuccess(false);
            response.setErrorMessage("Analysis failed: " + e.getMessage());
        }

        response.setExecutionTime(Duration.between(start, Instant.now()));
        return response;
    }

    private String executeExplainAnalyze(AnalysisRequest request) throws SQLException {
        String url = String.format("jdbc:postgresql://%s:%d/%s",
                request.getConnection().getHost(),
                request.getConnection().getPort(),
                request.getConnection().getDatabase());

        try (Connection conn = DriverManager.getConnection(
                url,
                request.getConnection().getUsername(),
                request.getConnection().getPassword())) {

            String explainQuery = "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + request.getSqlQuery();

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(explainQuery)) {

                if (rs.next()) {
                    return rs.getString(1); // JSON план
                }
                throw new SQLException("No execution plan returned");
            }
        }
    }
}