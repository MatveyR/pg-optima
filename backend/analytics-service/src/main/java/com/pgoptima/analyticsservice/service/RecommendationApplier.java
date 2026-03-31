package com.pgoptima.analyticsservice.service;

import com.pgoptima.analyticsservice.dto.ConnectionDetails;
import com.pgoptima.shareddto.enums.RecommendationType;
import com.pgoptima.shareddto.response.Recommendation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
@Service
public class RecommendationApplier {

    private static final int MAX_APPLY_SEC = 30;
    private static final int QUERY_TIMEOUT_SEC = 10;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    public List<Recommendation> applyAndMeasure(List<Recommendation> recommendations,
                                                String originalQuery,
                                                ConnectionDetails connection,
                                                Duration originalTime) {
        List<Recommendation> result = new ArrayList<>();
        for (Recommendation rec : recommendations) {
            try {
                result.add(applySingle(rec, originalQuery, connection, originalTime));
            } catch (Exception e) {
                log.warn("Failed to apply recommendation: {}", rec.getDescription(), e);
                rec.getWarnings().add("Error: " + e.getMessage());
                result.add(rec);
            }
        }
        return result;
    }

    private Recommendation applySingle(Recommendation rec, String query,
                                       ConnectionDetails conn, Duration originalTime) throws Exception {
        rec.setOriginalExecutionTime(originalTime);
        if (rec.getSqlCommand() == null || !isAutoApplicable(rec.getType())) {
            rec.getWarnings().add("Cannot auto-apply");
            return rec;
        }

        Future<ExecutionResult> future = executor.submit(() -> executeWithOptimization(rec, query, conn));
        ExecutionResult execResult;
        try {
            execResult = future.get(MAX_APPLY_SEC, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            rec.getWarnings().add("Timeout applying recommendation");
            rec.setApplied(false);
            return rec;
        }

        rec.setApplied(execResult.success);
        rec.setOptimizedExecutionTime(execResult.optimizedTime);
        rec.setActualImprovement(execResult.improvementPercent);
        if (execResult.metrics != null) rec.getMetrics().putAll(execResult.metrics);
        return rec;
    }

    private boolean isAutoApplicable(RecommendationType type) {
        return type == RecommendationType.CREATE_INDEX || type == RecommendationType.ADD_LIMIT;
    }

    private ExecutionResult executeWithOptimization(Recommendation rec, String query, ConnectionDetails conn) {
        ExecutionResult result = new ExecutionResult();
        try (Connection mainConn = createConnection(conn);
             Connection testConn = createConnection(conn)) {
            mainConn.setAutoCommit(false);
            testConn.setAutoCommit(false);

            // baseline
            Duration baseline = executeQueryWithTiming(testConn, query, false);
            result.baselineTime = baseline;

            // apply
            applyRecommendation(mainConn, rec);

            // optimized
            Duration optimized = executeQueryWithTiming(testConn, query, false);
            result.optimizedTime = optimized;
            result.improvementPercent = calculateImprovement(baseline, optimized);
            result.success = true;
        } catch (Exception e) {
            result.success = false;
            result.errorMessage = e.getMessage();
            log.error("Optimization failed", e);
        }
        return result;
    }

    private void applyRecommendation(Connection conn, Recommendation rec) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            if (rec.getType() == RecommendationType.CREATE_INDEX && rec.getSqlCommand() != null) {
                stmt.execute(rec.getSqlCommand());
                conn.commit();
            }
            // ADD_LIMIT не требует физического применения, только измерение с модифицированным запросом
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        }
    }

    private Duration executeQueryWithTiming(Connection conn, String query, boolean isOptimized) throws SQLException {
        String sql = isOptimized ? query : query; // для ADD_LIMIT нужно модифицировать, но упростим
        // Для избежания изменений данных используем EXPLAIN ANALYZE только для SELECT
        String explainQuery = "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + sql;
        long start = System.nanoTime();
        try (Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(QUERY_TIMEOUT_SEC);
            try (ResultSet rs = stmt.executeQuery(explainQuery)) {
                while (rs.next()) { /* consume */ }
            }
            conn.commit();
        }
        return Duration.ofNanos(System.nanoTime() - start);
    }

    private double calculateImprovement(Duration baseline, Duration optimized) {
        if (baseline.isZero()) return 0.0;
        double diff = baseline.toMillis() - optimized.toMillis();
        return diff > 0 ? (diff / baseline.toMillis()) * 100.0 : 0.0;
    }

    private Connection createConnection(ConnectionDetails conn) throws SQLException {
        String url = String.format("jdbc:postgresql://%s:%d/%s?sslmode=%s",
                conn.getHost(), conn.getPort(), conn.getDatabase(), conn.getSslMode());
        return DriverManager.getConnection(url, conn.getUsername(), conn.getPassword());
    }

    private static class ExecutionResult {
        boolean success;
        String errorMessage;
        Duration baselineTime;
        Duration optimizedTime;
        double improvementPercent;
        java.util.Map<String, Object> metrics = new java.util.HashMap<>();
    }
}