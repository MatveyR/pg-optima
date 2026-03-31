package com.pgoptima.analyticsservice.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.pgoptima.analyticsservice.config.PlanAnalyzerProperties;
import com.pgoptima.shareddto.enums.ImpactLevel;
import com.pgoptima.shareddto.enums.RecommendationType;
import com.pgoptima.shareddto.response.Recommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlanAnalyzer {

    private final PlanAnalyzerProperties properties;

    // Вспомогательный класс контекста
    private static class AnalysisContext {
        String originalQuery;
        Map<String, String> tableAliases = new HashMap<>();
        Set<String> whereColumns = new HashSet<>();
        Set<String> joinColumns = new HashSet<>();
        Set<String> orderByColumns = new HashSet<>();
        Set<String> groupByColumns = new HashSet<>();
        PlanMetrics metrics = new PlanMetrics();
        List<JsonNode> scanNodes = new ArrayList<>();
        List<JsonNode> joinNodes = new ArrayList<>();
        List<JsonNode> sortNodes = new ArrayList<>();
    }

    public List<Recommendation> analyze(JsonNode plan, String originalQuery) {
        List<Recommendation> recommendations = new ArrayList<>();
        if (plan == null || plan.isMissingNode()) {
            recommendations.add(createErrorRecommendation("План выполнения отсутствует"));
            return recommendations;
        }

        AnalysisContext context = new AnalysisContext();
        context.originalQuery = originalQuery;
        collectMetrics(plan, context.metrics);
        collectContextInfo(plan, context);
        analyzeOriginalQuery(context);
        analyzeScans(plan, recommendations, context);
        analyzeJoins(plan, recommendations, context);
        analyzeSortOperations(plan, recommendations, context);
        analyzeAggregations(plan, recommendations, context);
        analyzeMemoryUsage(plan, recommendations, context);
        analyzeParallelism(plan, recommendations, context);
        analyzeSubqueries(plan, recommendations, context);
        analyzeIndexUsage(plan, recommendations, context);
        generateOptimizedQueries(recommendations, context);
        return sortAndDeduplicate(recommendations);
    }

    private void collectMetrics(JsonNode node, PlanMetrics metrics) {
        if (node == null) return;
        collectMetricsRecursive(node, metrics, 0);
    }

    private void collectMetricsRecursive(JsonNode node, PlanMetrics metrics, int depth) {
        if (node == null || node.isMissingNode()) return;
        metrics.nodeCount++;
        metrics.maxDepth = Math.max(metrics.maxDepth, depth);

        String nodeType = getNodeText(node, "Node Type");
        metrics.totalCost += getNodeDouble(node, "Total Cost");
        metrics.actualTotalTime += getNodeDouble(node, "Actual Total Time");
        metrics.actualRows += getNodeLong(node, "Actual Rows");
        metrics.plannedRows += getNodeLong(node, "Plan Rows");

        switch (nodeType) {
            case "Seq Scan":
                metrics.seqScanCount++;
                long rows = getNodeLong(node, "Actual Rows");
                if (rows > metrics.maxSeqScanRows) {
                    metrics.maxSeqScanRows = rows;
                    metrics.largestSeqScanTable = getNodeText(node, "Relation Name");
                }
                break;
            case "Index Scan":
            case "Index Only Scan":
                metrics.indexScanCount++;
                if ("Index Only Scan".equals(nodeType)) metrics.indexOnlyScanCount++;
                String idx = getNodeText(node, "Index Name");
                if (!idx.isEmpty()) metrics.usedIndexes.add(idx);
                break;
            case "Bitmap Heap Scan":
                metrics.bitmapScanCount++;
                break;
            case "Nested Loop":
            case "Hash Join":
            case "Merge Join":
                metrics.joinCount++;
                metrics.maxJoinRows = Math.max(metrics.maxJoinRows, getNodeLong(node, "Actual Rows"));
                if ("Nested Loop".equals(nodeType)) metrics.nestedLoopCount++;
                else if ("Hash Join".equals(nodeType)) metrics.hashJoinCount++;
                else metrics.mergeJoinCount++;
                break;
            case "Sort":
                metrics.sortCount++;
                if ("Disk".equals(getNodeText(node, "Sort Space Type"))) {
                    metrics.diskSortCount++;
                    metrics.diskSortRows += getNodeLong(node, "Actual Rows");
                } else {
                    metrics.memorySortCount++;
                }
                break;
            case "Aggregate":
            case "HashAggregate":
            case "GroupAggregate":
                metrics.aggregateCount++;
                break;
            case "WindowAgg":
                metrics.windowFunctionCount++;
                break;
            case "CTE Scan":
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
            case "Recursive Union":
                metrics.recursiveUnionCount++;
                break;
        }

        metrics.sharedHitBlocks += getNodeLong(node, "Shared Hit Blocks");
        metrics.sharedReadBlocks += getNodeLong(node, "Shared Read Blocks");
        metrics.tempReadBlocks += getNodeLong(node, "Temp Read Blocks");
        metrics.tempWrittenBlocks += getNodeLong(node, "Temp Written Blocks");

        if (node.has("Filter")) metrics.filterCount++;
        if (node.has("Join Filter")) metrics.joinFilterCount++;
        if (node.has("Index Cond")) metrics.indexConditionCount++;

        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                collectMetricsRecursive(child, metrics, depth + 1);
            }
        }
    }

    private void collectContextInfo(JsonNode node, AnalysisContext context) {
        if (node == null) return;
        String nodeType = getNodeText(node, "Node Type");
        if (nodeType.contains("Scan")) context.scanNodes.add(node);
        else if (nodeType.contains("Join")) context.joinNodes.add(node);
        else if ("Sort".equals(nodeType)) context.sortNodes.add(node);

        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) collectContextInfo(child, context);
        }
    }

    private void analyzeOriginalQuery(AnalysisContext context) {
        if (context.originalQuery == null) return;
        try {
            net.sf.jsqlparser.statement.Statement stmt = CCJSqlParserUtil.parse(context.originalQuery);
            if (stmt instanceof Select) {
                PlainSelect select = (PlainSelect) ((Select) stmt).getSelectBody();
                if (select.getWhere() != null) {
                    extractColumns(select.getWhere(), context.whereColumns);
                }
                if (select.getJoins() != null) {
                    select.getJoins().forEach(join -> {
                        if (join.getOnExpression() != null) extractColumns(join.getOnExpression(), context.joinColumns);
                    });
                }
                if (select.getOrderByElements() != null) {
                    select.getOrderByElements().forEach(ob -> extractColumns(ob.getExpression(), context.orderByColumns));
                }
                if (select.getGroupBy() != null && select.getGroupBy().getGroupByExpressions() != null) {
                    select.getGroupBy().getGroupByExpressions().forEach(expr -> extractColumns(expr, context.groupByColumns));
                }
            }
        } catch (JSQLParserException e) {
            log.debug("JSqlParser failed, using heuristics");
            extractColumnsHeuristic(context.originalQuery, context.whereColumns);
        }
    }

    private void extractColumns(Expression expr, Set<String> target) {
        if (expr == null) return;
        expr.accept(new net.sf.jsqlparser.expression.ExpressionVisitorAdapter() {
            @Override
            public void visit(net.sf.jsqlparser.schema.Column column) {
                target.add(column.getColumnName());
            }
        });
    }

    private void extractColumnsHeuristic(String sql, Set<String> target) {
        Pattern p = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b");
        Matcher m = p.matcher(sql.toUpperCase());
        while (m.find()) {
            String word = m.group(1);
            if (!isKeyword(word)) target.add(word);
        }
    }

    private boolean isKeyword(String word) {
        Set<String> keywords = Set.of("SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "NULL",
                "JOIN", "ON", "AS", "IN", "LIKE", "BETWEEN", "IS", "ORDER", "BY", "GROUP", "HAVING");
        return keywords.contains(word);
    }

    private void analyzeScans(JsonNode node, List<Recommendation> recommendations, AnalysisContext ctx) {
        if (node == null) return;
        String nodeType = getNodeText(node, "Node Type");
        if ("Seq Scan".equals(nodeType)) {
            long rows = getNodeLong(node, "Actual Rows");
            String filter = getNodeText(node, "Filter");
            String table = getNodeText(node, "Relation Name");
            if (!filter.isEmpty() && rows > properties.getSeqScanThreshold()) {
                List<String> cols = new ArrayList<>(ctx.whereColumns);
                if (!cols.isEmpty()) {
                    String idxCmd = String.format("CREATE INDEX idx_%s_%s ON %s(%s);",
                            table, cols.get(0).toLowerCase(), table, String.join(",", cols));
                    addRecommendation(recommendations, RecommendationType.CREATE_INDEX,
                            "Seq Scan на " + table + " (" + rows + " строк). Создайте индекс.",
                            ImpactLevel.HIGH, 40.0, idxCmd);
                }
            }
        }
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) analyzeScans(child, recommendations, ctx);
        }
    }

    private void analyzeJoins(JsonNode node, List<Recommendation> recommendations, AnalysisContext ctx) {
        if (node == null) return;
        String nodeType = getNodeText(node, "Node Type");
        if (nodeType.contains("Join")) {
            if (!node.has("Join Filter") && !node.has("Hash Cond") && !node.has("Merge Cond")) {
                addRecommendation(recommendations, RecommendationType.REWRITE_QUERY,
                        "Cartesian join detected. Add join condition.",
                        ImpactLevel.HIGH, 70.0, null);
            }
        }
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) analyzeJoins(child, recommendations, ctx);
        }
    }

    private void analyzeSortOperations(JsonNode node, List<Recommendation> recommendations, AnalysisContext ctx) {
        if (node == null) return;
        if ("Sort".equals(getNodeText(node, "Node Type"))) {
            long rows = getNodeLong(node, "Actual Rows");
            if (rows > 5000 && !ctx.orderByColumns.isEmpty()) {
                String table = findTableForSort(node, ctx);
                if (table != null && !ctx.orderByColumns.isEmpty()) {
                    String idxCmd = String.format("CREATE INDEX idx_%s_sort ON %s(%s);",
                            table, table, String.join(",", ctx.orderByColumns));
                    addRecommendation(recommendations, RecommendationType.CREATE_INDEX,
                            "Sort of " + rows + " rows. Create index to avoid sort.",
                            ImpactLevel.MEDIUM, 35.0, idxCmd);
                }
            }
        }
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) analyzeSortOperations(child, recommendations, ctx);
        }
    }

    private String findTableForSort(JsonNode node, AnalysisContext ctx) {
        // ищем ближайший Scan узел
        JsonNode current = node;
        while (current != null) {
            if (getNodeText(current, "Node Type").contains("Scan")) {
                return getNodeText(current, "Relation Name");
            }
            JsonNode parent = current.get("Parent"); // нет прямой ссылки, упростим: возьмем первый scan из контекста
            break;
        }
        if (!ctx.scanNodes.isEmpty()) {
            return getNodeText(ctx.scanNodes.get(0), "Relation Name");
        }
        return null;
    }

    private void analyzeAggregations(JsonNode node, List<Recommendation> recommendations, AnalysisContext ctx) {
        // упрощённо
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) analyzeAggregations(child, recommendations, ctx);
        }
    }

    private void analyzeMemoryUsage(JsonNode node, List<Recommendation> recommendations, AnalysisContext ctx) {
        long tempRead = getNodeLong(node, "Temp Read Blocks");
        long tempWrite = getNodeLong(node, "Temp Written Blocks");
        if (tempRead + tempWrite > properties.getHighBufferThreshold()) {
            addRecommendation(recommendations, RecommendationType.INCREASE_WORK_MEM,
                    "Disk temporary operations detected. Increase work_mem.",
                    ImpactLevel.MEDIUM, 40.0, null);
        }
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) analyzeMemoryUsage(child, recommendations, ctx);
        }
    }

    private void analyzeParallelism(JsonNode node, List<Recommendation> recommendations, AnalysisContext ctx) {
        // упрощённо
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) analyzeParallelism(child, recommendations, ctx);
        }
    }

    private void analyzeSubqueries(JsonNode node, List<Recommendation> recommendations, AnalysisContext ctx) {
        String nodeType = getNodeText(node, "Node Type");
        if ("Subquery Scan".equals(nodeType) && getNodeLong(node, "Actual Rows") > 10000) {
            addRecommendation(recommendations, RecommendationType.REWRITE_QUERY,
                    "Subquery processes many rows. Consider rewriting as JOIN.",
                    ImpactLevel.MEDIUM, 30.0, null);
        }
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) analyzeSubqueries(child, recommendations, ctx);
        }
    }

    private void analyzeIndexUsage(JsonNode node, List<Recommendation> recommendations, AnalysisContext ctx) {
        // упрощённо
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) analyzeIndexUsage(child, recommendations, ctx);
        }
    }

    private void generateOptimizedQueries(List<Recommendation> recommendations, AnalysisContext ctx) {
        if (ctx.originalQuery != null && !ctx.originalQuery.toUpperCase().contains("LIMIT") &&
                ctx.metrics.actualRows > 1000) {
            addRecommendation(recommendations, RecommendationType.ADD_LIMIT,
                    "Add LIMIT to reduce result set.",
                    ImpactLevel.LOW, 15.0, ctx.originalQuery + " LIMIT 100");
        }
    }

    private void addRecommendation(List<Recommendation> list, RecommendationType type,
                                   String desc, ImpactLevel impact, Double improvement, String sqlCmd) {
        list.add(Recommendation.builder()
                .type(type)
                .description(desc)
                .impact(impact)
                .estimatedImprovement(improvement)
                .sqlCommand(sqlCmd)
                .sqlSuggestion(generateSqlSuggestion(type))
                .applied(false)
                .warnings(new ArrayList<>())
                .metrics(new HashMap<>())
                .build());
    }

    private String generateSqlSuggestion(RecommendationType type) {
        return switch (type) {
            case CREATE_INDEX -> "CREATE INDEX idx_name ON table(column);";
            case ADD_LIMIT -> "SELECT ... LIMIT N;";
            case INCREASE_WORK_MEM -> "SET work_mem = '64MB';";
            default -> "Review query execution plan.";
        };
    }

    private List<Recommendation> sortAndDeduplicate(List<Recommendation> recs) {
        Map<String, Recommendation> unique = new LinkedHashMap<>();
        for (Recommendation r : recs) {
            String key = r.getSqlCommand() != null ? r.getType() + ":" + r.getSqlCommand() : r.getType() + ":" + r.getDescription();
            unique.putIfAbsent(key, r);
        }
        List<Recommendation> list = new ArrayList<>(unique.values());
        list.sort((a, b) -> {
            double pa = getImpactWeight(a.getImpact()) * (a.getEstimatedImprovement() != null ? a.getEstimatedImprovement() : 0);
            double pb = getImpactWeight(b.getImpact()) * (b.getEstimatedImprovement() != null ? b.getEstimatedImprovement() : 0);
            return Double.compare(pb, pa);
        });
        return list.size() > 20 ? list.subList(0, 20) : list;
    }

    private double getImpactWeight(ImpactLevel level) {
        return switch (level) {
            case HIGH -> 3.0;
            case MEDIUM -> 2.0;
            default -> 1.0;
        };
    }

    private Recommendation createErrorRecommendation(String msg) {
        return Recommendation.builder()
                .type(RecommendationType.OTHER)
                .description(msg)
                .impact(ImpactLevel.HIGH)
                .estimatedImprovement(0.0)
                .applied(false)
                .warnings(List.of("Analysis error"))
                .build();
    }

    private String getNodeText(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asText() : "";
    }

    private long getNodeLong(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asLong() : 0L;
    }

    private double getNodeDouble(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asDouble() : 0.0;
    }
}