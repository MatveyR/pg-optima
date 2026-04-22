package com.pgoptima.analyticsservice.service.impl;

import com.pgoptima.analyticsservice.client.UserServiceClient;
import com.pgoptima.analyticsservice.config.PlanAnalyzerProperties;
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
import com.pgoptima.shareddto.response.ConnectionDTO;
import com.pgoptima.shareddto.response.ExecuteResponse;
import com.pgoptima.shareddto.response.Recommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public AnalysisResponse analyzeQuery(AnalysisRequest request, String authHeader) {
        log.info("Начало анализа: connectionId={}, длина запроса={}", request.getConnectionId(), request.getSqlQuery().length());
        Instant start = Instant.now();
        AnalysisResponse response = AnalysisResponse.builder()
                .originalQuery(request.getSqlQuery())
                .requestTimestamp(Instant.now())
                .success(false)
                .build();

        if (SqlQueryTypeDetector.isModifyingQuery(request.getSqlQuery()) && !properties.isAllowModifyingQueries()) {
            response.setErrorMessage("Модифицирующие запросы (INSERT/UPDATE/DELETE) не разрешены для анализа.");
            return response;
        }

        ConnectionDTO connection;
        try {
            connection = userServiceClient.getConnectionById(request.getConnectionId(), authHeader);
            if (connection.getPassword() == null) {
                throw new RuntimeException("Пароль подключения отсутствует");
            }
        } catch (Exception e) {
            log.error("Ошибка получения данных подключения: {}", e.getMessage(), e);
            response.setErrorMessage("Не удалось получить данные подключения: " + e.getMessage());
            return response;
        }

        PlanExecutionResult execResult;
        try {
            execResult = executeExplain(request.getSqlQuery(), connection);
        } catch (Exception e) {
            log.error("Ошибка базы данных при EXPLAIN: {}", e.getMessage(), e);
            response.setErrorMessage("Ошибка базы данных: " + e.getMessage());
            return response;
        }

        response.setExecutionPlanJson(execResult.planJson);
        response.setOriginalExecutionTimeMs(execResult.executionTimeMs);

        List<Recommendation> recommendations;
        try {
            JsonNode plan = PostgrePlanParser.parse(execResult.planJson);
            recommendations = planAnalyzer.analyze(plan, request.getSqlQuery());
        } catch (Exception e) {
            log.error("Ошибка анализа плана: {}", e.getMessage(), e);
            response.setErrorMessage("Ошибка анализа плана: " + e.getMessage());
            return response;
        }

        response.setRecommendations(recommendations);
        response.setSuccess(true);
        response.setAnalysisDurationMs(Duration.between(start, Instant.now()).toMillis());
        log.info("Анализ завершён за {} мс, найдено рекомендаций: {}", response.getAnalysisDurationMs(), recommendations.size());
        return response;
    }

    @Override
    public ExecuteResponse executeQuery(ExecuteRequest request, String authHeader) {
        log.info("Выполнение запроса для connectionId={}", request.getConnectionId());
        Instant start = Instant.now();

        ConnectionDTO connection;
        try {
            connection = userServiceClient.getConnectionById(request.getConnectionId(), authHeader);
            if (connection.getPassword() == null) {
                throw new RuntimeException("Пароль отсутствует");
            }
        } catch (Exception e) {
            log.error("Ошибка получения подключения: {}", e.getMessage(), e);
            return ExecuteResponse.builder()
                    .success(false)
                    .errorMessage("Ошибка получения подключения: " + e.getMessage())
                    .build();
        }

        String url = String.format("jdbc:postgresql://%s:%d/%s?sslmode=%s",
                connection.getHost(), connection.getPort(), connection.getDatabase(),
                connection.getSslMode() != null ? connection.getSslMode() : "disable");

        try (Connection conn = DriverManager.getConnection(url, connection.getUsername(), connection.getPassword())) {
            try (Statement stmt = conn.createStatement()) {
                stmt.setFetchSize(1000);
                boolean isSelect = stmt.execute(request.getQuery());
                if (!isSelect) {
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
                        if (rowCount > 10000) {
                            log.warn("Результат обрезан на 10000 строк");
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
            log.error("Ошибка выполнения запроса: {}", e.getMessage(), e);
            return ExecuteResponse.builder()
                    .success(false)
                    .errorMessage("SQL ошибка: " + e.getMessage())
                    .build();
        }
    }

    private PlanExecutionResult executeExplain(String sql, ConnectionDTO conn) throws Exception {
        String url = String.format("jdbc:postgresql://%s:%d/%s?sslmode=%s",
                conn.getHost(), conn.getPort(), conn.getDatabase(),
                conn.getSslMode() != null ? conn.getSslMode() : "disable");
        try (Connection connection = DriverManager.getConnection(url, conn.getUsername(), conn.getPassword())) {
            String explainSql = "EXPLAIN (ANALYZE, BUFFERS, VERBOSE, FORMAT JSON) " + sql;
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(explainSql)) {
                String planJson = rs.next() ? rs.getString(1) : null;
                long actualTimeMs = 0;
                if (planJson != null) {
                    JsonNode root = PostgrePlanParser.parse(planJson);
                    actualTimeMs = (long) root.path("Actual Total Time").asDouble(0);
                }
                return new PlanExecutionResult(planJson, actualTimeMs);
            }
        }
    }

    private static class PlanExecutionResult {
        String planJson;
        long executionTimeMs;
        PlanExecutionResult(String planJson, long executionTimeMs) {
            this.planJson = planJson;
            this.executionTimeMs = executionTimeMs;
        }
    }
}