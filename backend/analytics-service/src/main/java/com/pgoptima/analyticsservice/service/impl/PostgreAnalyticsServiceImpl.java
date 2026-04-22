package com.pgoptima.analyticsservice.service.impl;

import com.pgoptima.analyticsservice.client.UserServiceClient;
import com.pgoptima.analyticsservice.config.PlanAnalyzerProperties;
import com.pgoptima.analyticsservice.dto.ConnectionDetails;
import com.pgoptima.analyticsservice.repository.OptimizationHistoryRepository;
import com.pgoptima.analyticsservice.repository.RecommendationResultRepository;
import com.pgoptima.analyticsservice.service.AnalyticsService;
import com.pgoptima.analyticsservice.util.PlanAnalyzer;
import com.pgoptima.analyticsservice.util.PostgrePlanParser;
import com.pgoptima.analyticsservice.util.SqlQueryTypeDetector;
import com.fasterxml.jackson.databind.JsonNode;
import com.pgoptima.shareddto.request.AnalysisRequest;
import com.pgoptima.shareddto.request.ExecuteRequest;
import com.pgoptima.shareddto.response.AnalysisResponse;
import com.pgoptima.shareddto.response.ExecuteResponse;
import com.pgoptima.shareddto.response.Recommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

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
    private final PlanAnalyzerProperties properties;

    @Override
    @Cacheable(value = "analysisResults", key = "#request.connectionId + '-' + #request.sqlQuery.hashCode()", unless = "#result.success == false")
    public AnalysisResponse analyzeQuery(AnalysisRequest request, String authHeader) {
        log.info("Starting analysis for connectionId={}, query length={}", request.getConnectionId(), request.getSqlQuery().length());
        Instant start = Instant.now();
        AnalysisResponse response = AnalysisResponse.builder()
                .originalQuery(request.getSqlQuery())
                .requestTimestamp(Instant.now())
                .success(false)
                .build();

        // Проверка на модифицирующие запросы
        if (SqlQueryTypeDetector.isModifyingQuery(request.getSqlQuery()) && !properties.isAllowModifyingQueries()) {
            response.setErrorMessage("Modifying queries (INSERT/UPDATE/DELETE) are not allowed for analysis.");
            log.warn("Rejected modifying query: {}", request.getSqlQuery());
            return response;
        }

        // Получение деталей подключения
        ConnectionDetails connection;
        try {
            connection = userServiceClient.getConnectionById(request.getConnectionId(), authHeader);
        } catch (Exception e) {
            log.error("Failed to fetch connection details: {}", e.getMessage(), e);
            response.setErrorMessage("Failed to fetch connection details: " + e.getMessage());
            return response;
        }

        // Выполнение EXPLAIN
        PlanExecutionResult execResult;
        try {
            execResult = executeExplain(request.getSqlQuery(), connection);
        } catch (SQLException e) {
            log.error("Database error during EXPLAIN: {}", e.getMessage(), e);
            response.setErrorMessage("Database error: " + e.getMessage());
            return response;
        }

        response.setExecutionPlanJson(execResult.planJson);
        response.setOriginalExecutionTime(execResult.executionTime);

        // Анализ плана
        List<Recommendation> recommendations;
        try {
            JsonNode plan = PostgrePlanParser.parse(execResult.planJson);
            recommendations = planAnalyzer.analyze(plan, request.getSqlQuery());
        } catch (Exception e) {
            log.error("Plan analysis failed: {}", e.getMessage(), e);
            response.setErrorMessage("Plan analysis failed: " + e.getMessage());
            return response;
        }

        response.setRecommendations(recommendations);
        response.setSuccess(true);
        response.setAnalysisDuration(Duration.between(start, Instant.now()));
        log.info("Analysis completed in {} ms, found {} recommendations", response.getAnalysisDuration().toMillis(), recommendations.size());

        return response;
    }

    @Override
    public ExecuteResponse executeQuery(ExecuteRequest request, String authHeader) {
        log.info("Executing query for connectionId={}", request.getConnectionId());
        Instant start = Instant.now();

        ConnectionDetails connection;
        try {
            connection = userServiceClient.getConnectionById(request.getConnectionId(), authHeader);
        } catch (Exception e) {
            log.error("Failed to fetch connection details: {}", e.getMessage(), e);
            return ExecuteResponse.builder()
                    .success(false)
                    .errorMessage("Failed to fetch connection details: " + e.getMessage())
                    .build();
        }

        String url = String.format("jdbc:postgresql://%s:%d/%s?sslmode=%s",
                connection.getHost(), connection.getPort(), connection.getDatabase(), connection.getSslMode());

        try (Connection conn = DriverManager.getConnection(url, connection.getUsername(), connection.getPassword())) {
            try (Statement stmt = conn.createStatement()) {
                stmt.setFetchSize(1000);
                boolean isSelect = stmt.execute(request.getQuery());
                if (!isSelect) {
                    // Для не-SELECT запросов возвращаем количество затронутых строк
                    int updateCount = stmt.getUpdateCount();
                    long duration = Duration.between(start, Instant.now()).toMillis();
                    return ExecuteResponse.builder()
                            .success(true)
                            .rowCount(updateCount)
                            .executionTimeMs(duration)
                            .columns(List.of("affected_rows"))
                            .rows(List.of(List.of(updateCount)))
                            .build();
                }

                try (ResultSet rs = stmt.getResultSet()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();
                    List<String> columns = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        columns.add(meta.getColumnLabel(i));
                    }

                    List<List<Object>> rows = new ArrayList<>();
                    int rowCount = 0;
                    while (rs.next()) {
                        List<Object> row = new ArrayList<>();
                        for (int i = 1; i <= columnCount; i++) {
                            row.add(rs.getObject(i));
                        }
                        rows.add(row);
                        rowCount++;
                        // Ограничим количество строк, чтобы не перегружать память (например, 10000)
                        if (rowCount > 10000) {
                            log.warn("Result set truncated at 10000 rows");
                            break;
                        }
                    }
                    long duration = Duration.between(start, Instant.now()).toMillis();
                    return ExecuteResponse.builder()
                            .success(true)
                            .columns(columns)
                            .rows(rows)
                            .rowCount(rowCount)
                            .executionTimeMs(duration)
                            .build();
                }
            }
        } catch (SQLException e) {
            log.error("Query execution failed: {}", e.getMessage(), e);
            return ExecuteResponse.builder()
                    .success(false)
                    .errorMessage("SQL error: " + e.getMessage())
                    .build();
        }
    }

    private PlanExecutionResult executeExplain(String sql, ConnectionDetails conn) throws SQLException {
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

    private static class PlanExecutionResult {
        String planJson;
        Duration executionTime;
        PlanExecutionResult(String planJson, Duration executionTime) {
            this.planJson = planJson;
            this.executionTime = executionTime;
        }
    }
}