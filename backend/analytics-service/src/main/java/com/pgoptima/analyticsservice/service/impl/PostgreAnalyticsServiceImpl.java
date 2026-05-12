package com.pgoptima.analyticsservice.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.pgoptima.analyticsservice.client.UserServiceClient;
import com.pgoptima.analyticsservice.config.PlanAnalyzerProperties;
import com.pgoptima.analyticsservice.service.AnalyticsService;
import com.pgoptima.analyticsservice.util.PlanAnalyzer;
import com.pgoptima.analyticsservice.util.PostgrePlanParser;
import com.pgoptima.analyticsservice.util.SqlQueryParser;
import com.pgoptima.analyticsservice.util.SqlQueryTypeDetector;
import com.pgoptima.shareddto.request.AnalysisRequest;
import com.pgoptima.shareddto.request.ExecuteRequest;
import com.pgoptima.shareddto.response.AnalysisResponse;
import com.pgoptima.shareddto.response.ConnectionDTO;
import com.pgoptima.shareddto.response.ExecuteResponse;
import com.pgoptima.shareddto.response.Recommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
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
    private final SqlQueryParser sqlQueryParser;

    @Override
    public AnalysisResponse analyzeQuery(AnalysisRequest request, String authHeader) {
        log.info("Starting analysis for connectionId={}, iterations={}, apply={}", request.getConnectionId(), request.getIterations(), request.getApplyRecommendations());
        int iterations = request.getIterations() != null ? request.getIterations() : 1;
        boolean apply = request.getApplyRecommendations() != null && request.getApplyRecommendations();
        Instant globalStart = Instant.now();
        AnalysisResponse response = AnalysisResponse.builder()
                .originalQuery(request.getSqlQuery())
                .requestTimestamp(Instant.now())
                .success(false)
                .build();

        if (SqlQueryTypeDetector.isModifyingQuery(request.getSqlQuery()) && !properties.isAllowModifyingQueries()) {
            response.setErrorMessage("Modifying queries not allowed for analysis");
            return response;
        }

        ConnectionDTO connection;
        try {
            connection = userServiceClient.getConnectionById(request.getConnectionId(), authHeader);
            if (connection.getPassword() == null) throw new RuntimeException("Password missing");
        } catch (Exception e) {
            log.error("Failed to fetch connection details: {}", e.getMessage(), e);
            response.setErrorMessage("Failed to fetch connection details: " + e.getMessage());
            return response;
        }

        PlanExecutionResult originalExecResult;
        try {
            originalExecResult = executeExplain(request.getSqlQuery(), connection, iterations);
            if (!originalExecResult.success) {
                response.setErrorMessage("Original query execution failed");
                return response;
            }
        } catch (SQLException e) {
            log.error("Database error during EXPLAIN: {}", e.getMessage(), e);
            response.setErrorMessage("Database error: " + e.getMessage());
            return response;
        }

        response.setExecutionPlanJson(originalExecResult.planJson);
        response.setOriginalExecutionTimeMs(originalExecResult.avgExecutionTimeMs);

        List<Recommendation> recommendations;
        try {
            JsonNode plan = PostgrePlanParser.parse(originalExecResult.planJson);
            recommendations = planAnalyzer.analyze(plan, request.getSqlQuery());
        } catch (Exception e) {
            log.error("Plan analysis failed: {}", e.getMessage(), e);
            response.setErrorMessage("Plan analysis failed: " + e.getMessage());
            return response;
        }

        if (apply && !recommendations.isEmpty()) {
            log.info("Applying recommendations with rollback for {} items", recommendations.size());
            String url = String.format("jdbc:postgresql://%s:%d/%s?sslmode=%s",
                    connection.getHost(), connection.getPort(), connection.getDatabase(),
                    connection.getSslMode() != null ? connection.getSslMode() : "disable");
            for (int i = 0; i < recommendations.size(); i++) {
                Recommendation rec = recommendations.get(i);
                if (rec.getSqlCommand() == null || rec.getType().name().equals("OTHER")) continue;
                if (rec.getSqlCommand().contains("таблица") || rec.getSqlCommand().contains("column")) {
                    rec.getWarnings().add("Cannot apply: generated SQL contains placeholders");
                    continue;
                }
                try {
                    long originalAvg = originalExecResult.avgExecutionTimeMs;
                    if (rec.getType().name().equals("ADD_LIMIT")) {
                        String modifiedSql = addLimitToQuery(request.getSqlQuery(), 100);
                        PlanExecutionResult optimizedResult = executeExplain(modifiedSql, connection, iterations);
                        rec.setOptimizedExecutionTime(Duration.ofMillis(optimizedResult.avgExecutionTimeMs));
                        double improvement = originalAvg > 0 ? ((originalAvg - optimizedResult.avgExecutionTimeMs) / (double) originalAvg) * 100.0 : 0;
                        rec.setActualImprovement(Math.max(0, improvement));
                        rec.setApplied(true);
                        rec.setOptimizedQuery(modifiedSql);
                    } else if (rec.getType().name().equals("CREATE_INDEX")) {
                        String indexName = extractIndexName(rec.getSqlCommand());
                        try (Connection conn = DriverManager.getConnection(url, connection.getUsername(), connection.getPassword())) {
                            conn.setAutoCommit(true);
                            String safeCommand = sanitizeIndexCommand(rec.getSqlCommand());
                            try (java.sql.Statement stmt = conn.createStatement()) {
                                stmt.execute(safeCommand);
                            }
                        }
                        PlanExecutionResult optimizedResult = executeExplain(request.getSqlQuery(), connection, iterations);
                        double improvement = originalAvg > 0 ? ((originalAvg - optimizedResult.avgExecutionTimeMs) / (double) originalAvg) * 100.0 : 0;
                        rec.setActualImprovement(Math.max(0, improvement));
                        rec.setApplied(true);
                        try (Connection conn = DriverManager.getConnection(url, connection.getUsername(), connection.getPassword())) {
                            conn.setAutoCommit(true);
                            try (java.sql.Statement stmt = conn.createStatement()) {
                                stmt.execute("DROP INDEX IF EXISTS " + indexName);
                            }
                        }
                    } else if (rec.getType().name().equals("INCREASE_WORK_MEM")) {
                        try (Connection conn = DriverManager.getConnection(url, connection.getUsername(), connection.getPassword())) {
                            conn.setAutoCommit(true);
                            try (java.sql.Statement stmt = conn.createStatement()) {
                                stmt.execute(rec.getSqlCommand());
                            }
                        }
                        PlanExecutionResult optimizedResult = executeExplain(request.getSqlQuery(), connection, iterations);
                        double improvement = originalAvg > 0 ? ((originalAvg - optimizedResult.avgExecutionTimeMs) / (double) originalAvg) * 100.0 : 0;
                        rec.setActualImprovement(Math.max(0, improvement));
                        rec.setApplied(true);
                    } else if (rec.getType().name().equals("UPDATE_STATISTICS")) {
                        try (Connection conn = DriverManager.getConnection(url, connection.getUsername(), connection.getPassword())) {
                            conn.setAutoCommit(true);
                            try (java.sql.Statement stmt = conn.createStatement()) {
                                stmt.execute(rec.getSqlCommand());
                            }
                        }
                        PlanExecutionResult optimizedResult = executeExplain(request.getSqlQuery(), connection, iterations);
                        double improvement = originalAvg > 0 ? ((originalAvg - optimizedResult.avgExecutionTimeMs) / (double) originalAvg) * 100.0 : 0;
                        rec.setActualImprovement(Math.max(0, improvement));
                        rec.setApplied(true);
                    } else {
                        rec.getWarnings().add("Auto-apply not supported for this recommendation type");
                    }
                } catch (Exception e) {
                    log.warn("Failed to apply recommendation {}: {}", rec.getType(), e.getMessage());
                    rec.getWarnings().add("Failed to apply: " + e.getMessage());
                    rec.setApplied(false);
                }
            }
        }

        response.setRecommendations(recommendations);
        response.setSuccess(true);
        response.setAnalysisDurationMs(Duration.between(globalStart, Instant.now()).toMillis());
        log.info("Analysis completed in {} ms", response.getAnalysisDurationMs());
        return response;
    }

    private String extractIndexName(String sqlCommand) {
        String pattern = "CREATE\\s+(?:CONCURRENTLY\\s+)?INDEX\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(\\w+)";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(sqlCommand);
        if (m.find()) return m.group(1);
        return "idx_" + System.currentTimeMillis();
    }

    @Override
    public ExecuteResponse executeQuery(ExecuteRequest request, String authHeader) {
        log.info("Executing query for connectionId={}, iterations={}", request.getConnectionId(), request.getIterations());
        int iterations = request.getIterations() != null ? request.getIterations() : 1;
        ConnectionDTO connection;
        try {
            connection = userServiceClient.getConnectionById(request.getConnectionId(), authHeader);
            if (connection.getPassword() == null) throw new RuntimeException("Password missing");
        } catch (Exception e) {
            log.error("Failed to fetch connection details: {}", e.getMessage(), e);
            return ExecuteResponse.builder().success(false).errorMessage("Failed to fetch connection details: " + e.getMessage()).build();
        }
        String url = String.format("jdbc:postgresql://%s:%d/%s?sslmode=%s",
                connection.getHost(), connection.getPort(), connection.getDatabase(),
                connection.getSslMode() != null ? connection.getSslMode() : "disable");
        List<String> lastColumns = null;
        List<List<Object>> lastRows = null;
        long totalDuration = 0;
        int totalRowCount = 0;
        boolean anySuccess = false;
        for (int iter = 0; iter < iterations; iter++) {
            try (Connection conn = DriverManager.getConnection(url, connection.getUsername(), connection.getPassword())) {
                try (java.sql.Statement stmt = conn.createStatement()) {
                    stmt.setFetchSize(1000);
                    Instant start = Instant.now();
                    boolean isSelect = stmt.execute(request.getQuery());
                    if (!isSelect) {
                        int updateCount = stmt.getUpdateCount();
                        long duration = Duration.between(start, Instant.now()).toMillis();
                        totalDuration += duration;
                        totalRowCount += updateCount;
                        lastRows = List.of(List.of(updateCount));
                        lastColumns = List.of("affected_rows");
                        anySuccess = true;
                    } else {
                        try (ResultSet rs = stmt.getResultSet()) {
                            ResultSetMetaData meta = rs.getMetaData();
                            int columnCount = meta.getColumnCount();
                            List<String> columns = new ArrayList<>();
                            for (int i = 1; i <= columnCount; i++) columns.add(meta.getColumnLabel(i));
                            List<List<Object>> rows = new ArrayList<>();
                            int rowCount = 0;
                            while (rs.next()) {
                                List<Object> row = new ArrayList<>();
                                for (int i = 1; i <= columnCount; i++) row.add(rs.getObject(i));
                                rows.add(row);
                                rowCount++;
                                if (rowCount > 10000) break;
                            }
                            long duration = Duration.between(start, Instant.now()).toMillis();
                            totalDuration += duration;
                            totalRowCount += rowCount;
                            lastColumns = columns;
                            lastRows = rows;
                            anySuccess = true;
                        }
                    }
                }
            } catch (SQLException e) {
                log.error("Iteration {} failed: {}", iter, e.getMessage());
                if (!anySuccess) return ExecuteResponse.builder().success(false).errorMessage("SQL error: " + e.getMessage()).build();
            }
        }
        long avgDuration = totalDuration / iterations;
        int avgRowCount = totalRowCount / iterations;
        return ExecuteResponse.builder()
                .success(true)
                .columns(lastColumns)
                .rows(lastRows)
                .rowCount(avgRowCount)
                .executionTimeMs(avgDuration)
                .build();
    }

    private PlanExecutionResult executeExplain(String sql, ConnectionDTO conn, int iterations) throws SQLException {
        String url = String.format("jdbc:postgresql://%s:%d/%s?sslmode=%s",
                conn.getHost(), conn.getPort(), conn.getDatabase(),
                conn.getSslMode() != null ? conn.getSslMode() : "disable");
        long totalTime = 0;
        String planJson = null;
        boolean success = false;
        for (int i = 0; i < iterations; i++) {
            try (Connection connection = DriverManager.getConnection(url, conn.getUsername(), conn.getPassword())) {
                String explainSql = "EXPLAIN (ANALYZE, BUFFERS, VERBOSE, FORMAT JSON) " + sql;
                try (java.sql.Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(explainSql)) {
                    if (rs.next()) {
                        planJson = rs.getString(1);
                        if (planJson != null) {
                            JsonNode root = PostgrePlanParser.parse(planJson);
                            double actualTimeMs = root.path("Actual Total Time").asDouble(0);
                            totalTime += actualTimeMs;
                            success = true;
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if (!success) throw new SQLException("Explain failed for all iterations");
        return new PlanExecutionResult(planJson, totalTime / iterations);
    }

    private String addLimitToQuery(String originalSql, int limit) {
        try {
            net.sf.jsqlparser.statement.Statement stmt = CCJSqlParserUtil.parse(originalSql);
            if (stmt instanceof Select) {
                Select select = (Select) stmt;
                if (select.getSelectBody() instanceof PlainSelect) {
                    PlainSelect plain = (PlainSelect) select.getSelectBody();
                    if (plain.getLimit() == null) {
                        Limit limitClause = new Limit();
                        limitClause.setRowCount(new LongValue(limit));
                        plain.setLimit(limitClause);
                        String modified = select.toString();
                        if (originalSql.trim().endsWith(";")) {
                            modified = modified + ";";
                        }
                        return modified;
                    }
                }
            }
            return originalSql + " LIMIT " + limit;
        } catch (JSQLParserException e) {
            return originalSql + " LIMIT " + limit;
        }
    }

    private String sanitizeIndexCommand(String sqlCmd) {
        sqlCmd = sqlCmd.replaceAll("(?i)CONCURRENTLY\\s*", "");
        sqlCmd = sqlCmd.replaceAll("\\s+", " ").trim();
        return sqlCmd;
    }

    private static class PlanExecutionResult {
        String planJson;
        long avgExecutionTimeMs;
        boolean success;
        PlanExecutionResult(String planJson, long avgExecutionTimeMs) {
            this.planJson = planJson;
            this.avgExecutionTimeMs = avgExecutionTimeMs;
            this.success = true;
        }
    }
}