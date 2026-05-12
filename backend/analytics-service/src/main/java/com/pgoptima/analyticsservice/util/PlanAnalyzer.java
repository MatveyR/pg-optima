package com.pgoptima.analyticsservice.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.pgoptima.analyticsservice.config.PlanAnalyzerProperties;
import com.pgoptima.shareddto.enums.ImpactLevel;
import com.pgoptima.shareddto.enums.RecommendationType;
import com.pgoptima.shareddto.response.Recommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlanAnalyzer {

    private final PlanAnalyzerProperties properties;

    private static class AnalysisContext {
        String originalQuery;
        List<JsonNode> scanNodes = new ArrayList<>();
        List<JsonNode> joinNodes = new ArrayList<>();
        PlanMetrics metrics = new PlanMetrics();
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

        analyzeSeqScans(recommendations, context);
        analyzeJoinIndexes(recommendations, context);
        analyzeSorts(recommendations, context);
        analyzeMissingLimit(recommendations, context);
        analyzeDiskTemporaryFiles(recommendations, context);
        analyzeOutdatedStats(recommendations, context);

        sortRecommendationsByImpact(recommendations);
        return sortAndDeduplicate(recommendations);
    }

    private void analyzeSeqScans(List<Recommendation> recommendations, AnalysisContext ctx) {
        for (JsonNode node : ctx.scanNodes) {
            String nodeType = getNodeText(node, "Node Type");
            if ("Seq Scan".equals(nodeType)) {
                long rows = getNodeLong(node, "Actual Rows");
                long totalRows = getNodeLong(node, "Plan Rows");
                String filter = getNodeText(node, "Filter");
                String table = getNodeText(node, "Relation Name");
                if (table == null || table.isEmpty() || table.equals("?")) continue;
                if (!filter.isEmpty()) {
                    List<String> cols = extractColumnsFromFilter(filter);
                    if (!cols.isEmpty()) {
                        double improvement = calculateIndexImprovement(rows, totalRows, filter);
                        String indexCmd = String.format("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_%s_%s ON %s(%s);",
                                table, cols.get(0).toLowerCase(), table, String.join(", ", cols));
                        addRecommendation(recommendations, RecommendationType.CREATE_INDEX,
                                String.format("Полное сканирование таблицы «%s» (обработано %d строк). Создайте индекс для ускорения фильтрации.", table, rows),
                                ImpactLevel.HIGH, improvement, indexCmd);
                    }
                }
            }
        }
    }

    private void analyzeJoinIndexes(List<Recommendation> recommendations, AnalysisContext ctx) {
        for (JsonNode joinNode : ctx.joinNodes) {
            String joinType = getNodeText(joinNode, "Node Type");
            if (!joinType.contains("Join")) continue;
            String hashCond = getNodeText(joinNode, "Hash Cond");
            String mergeCond = getNodeText(joinNode, "Merge Cond");
            String joinFilter = getNodeText(joinNode, "Join Filter");
            String condition = null;
            if (!hashCond.isEmpty()) condition = hashCond;
            else if (!mergeCond.isEmpty()) condition = mergeCond;
            else if (!joinFilter.isEmpty()) condition = joinFilter;
            if (condition == null) continue;
            List<String> columns = extractColumnsFromCondition(condition);
            if (columns.isEmpty()) continue;
            String leftTable = extractTableFromPlan(joinNode, "Plans", 0);
            String rightTable = extractTableFromPlan(joinNode, "Plans", 1);
            String targetTable = leftTable != null ? leftTable : rightTable;
            if (targetTable == null) targetTable = "table";
            String indexCmd = String.format("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_%s_join ON %s(%s);",
                    targetTable, targetTable, String.join(", ", columns));
            addRecommendation(recommendations, RecommendationType.CREATE_INDEX,
                    "Для ускорения JOIN-операций создайте индекс на столбцах: " + String.join(", ", columns),
                    ImpactLevel.MEDIUM, 30.0, indexCmd);
        }
    }

    private void analyzeSorts(List<Recommendation> recommendations, AnalysisContext ctx) {
        if (ctx.metrics.sortCount == 0) return;
        boolean diskSort = ctx.metrics.diskSortCount > 0;
        double improvement = diskSort ? 70.0 : 35.0;
        String suggestion = diskSort ? "Сортировка выполняется на диске. Увеличьте work_mem." : "Сортировка больших объёмов данных без индекса. Увеличьте work_mem.";
        addRecommendation(recommendations, RecommendationType.INCREASE_WORK_MEM, suggestion,
                diskSort ? ImpactLevel.HIGH : ImpactLevel.MEDIUM, improvement, "SET work_mem = '64MB';");
    }

    private void analyzeMissingLimit(List<Recommendation> recommendations, AnalysisContext ctx) {
        if (ctx.metrics.actualRows > 1000 && ctx.originalQuery != null && !ctx.originalQuery.toUpperCase().contains("LIMIT")) {
            double improvement = Math.min(90.0, (1.0 - 100.0 / ctx.metrics.actualRows) * 100.0);
            String modifiedQuery = addLimitToQuery(ctx.originalQuery, 100);
            addRecommendation(recommendations, RecommendationType.ADD_LIMIT,
                    String.format("Запрос возвращает %d строк. Добавьте LIMIT 100.", ctx.metrics.actualRows),
                    ImpactLevel.MEDIUM, improvement, modifiedQuery);
        }
    }

    private void analyzeDiskTemporaryFiles(List<Recommendation> recommendations, AnalysisContext ctx) {
        if (ctx.metrics.tempWrittenBlocks > properties.getHighBufferThreshold()) {
            addRecommendation(recommendations, RecommendationType.INCREASE_WORK_MEM,
                    "Запрос использует временные файлы на диске (" + ctx.metrics.tempWrittenBlocks + " блоков). Увеличьте work_mem.",
                    ImpactLevel.HIGH, 40.0, "SET work_mem = '128MB';");
        }
    }

    private void analyzeOutdatedStats(List<Recommendation> recommendations, AnalysisContext ctx) {
        if (ctx.metrics.actualRows > 0 && ctx.metrics.plannedRows > 0) {
            double ratio = Math.abs(ctx.metrics.actualRows - ctx.metrics.plannedRows) / (double) ctx.metrics.actualRows;
            if (ratio > 2.0) {
                double improvement = Math.min(80.0, ratio * 20.0);
                addRecommendation(recommendations, RecommendationType.UPDATE_STATISTICS,
                        "Статистика устарела (оценка строк отличается более чем в 2 раза). Выполните ANALYZE.",
                        ImpactLevel.HIGH, improvement, "ANALYZE;");
            }
        }
    }

    private List<String> extractColumnsFromFilter(String filter) {
        List<String> cols = new ArrayList<>();
        if (filter == null || filter.isEmpty()) return cols;
        String normalized = filter.replaceAll("::[a-zA-Z]+", "");
        Pattern p = Pattern.compile("\\b([a-z_][a-z0-9_]*)\\s*(?:=|>|<|>=|<=|LIKE|IN)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(normalized);
        while (m.find()) {
            String col = m.group(1);
            if (!cols.contains(col) && !col.equalsIgnoreCase("null") && !col.equalsIgnoreCase("unknown")) {
                cols.add(col);
            }
        }
        return cols;
    }

    private List<String> extractColumnsFromCondition(String condition) {
        List<String> cols = new ArrayList<>();
        if (condition == null || condition.isEmpty()) return cols;
        try {
            Expression expr = CCJSqlParserUtil.parseCondExpression(condition);
            Deque<Expression> stack = new ArrayDeque<>();
            stack.push(expr);
            while (!stack.isEmpty()) {
                Expression current = stack.pop();
                if (current instanceof Column) {
                    cols.add(((Column) current).getColumnName());
                } else if (current instanceof BinaryExpression) {
                    BinaryExpression bin = (BinaryExpression) current;
                    if (bin.getLeftExpression() != null) stack.push(bin.getLeftExpression());
                    if (bin.getRightExpression() != null) stack.push(bin.getRightExpression());
                } else if (current instanceof LikeExpression) {
                    LikeExpression like = (LikeExpression) current;
                    if (like.getLeftExpression() != null) stack.push(like.getLeftExpression());
                    if (like.getRightExpression() != null) stack.push(like.getRightExpression());
                } else if (current instanceof InExpression) {
                    InExpression in = (InExpression) current;
                    if (in.getLeftExpression() != null) stack.push(in.getLeftExpression());
                } else if (current instanceof Between) {
                    Between between = (Between) current;
                    if (between.getLeftExpression() != null) stack.push(between.getLeftExpression());
                } else if (current instanceof AndExpression || current instanceof OrExpression) {
                    if (current instanceof AndExpression) {
                        AndExpression and = (AndExpression) current;
                        if (and.getLeftExpression() != null) stack.push(and.getLeftExpression());
                        if (and.getRightExpression() != null) stack.push(and.getRightExpression());
                    } else {
                        OrExpression or = (OrExpression) current;
                        if (or.getLeftExpression() != null) stack.push(or.getLeftExpression());
                        if (or.getRightExpression() != null) stack.push(or.getRightExpression());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse condition: {}", condition);
        }
        return cols;
    }

    private String extractTableFromPlan(JsonNode node, String childKey, int index) {
        JsonNode children = node.get(childKey);
        if (children != null && children.isArray() && children.size() > index) {
            JsonNode child = children.get(index);
            String relName = getNodeText(child, "Relation Name");
            if (relName != null && !relName.isEmpty()) return relName;
            return extractTableFromPlan(child, childKey, 0);
        }
        return null;
    }

    private String addLimitToQuery(String originalSql, int limit) {
        try {
            String trimmed = originalSql.trim();
            boolean hasSemicolon = trimmed.endsWith(";");
            String base = hasSemicolon ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
            if (base.toUpperCase().contains("LIMIT")) return originalSql;
            String result = base + " LIMIT " + limit;
            if (hasSemicolon) result += ";";
            return result;
        } catch (Exception e) {
            return originalSql + " LIMIT " + limit;
        }
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
        if ("Seq Scan".equals(nodeType)) metrics.seqScanCount++;
        else if ("Index Scan".equals(nodeType)) metrics.indexScanCount++;
        else if ("Sort".equals(nodeType)) metrics.sortCount++;
        else if (nodeType.contains("Join")) metrics.joinCount++;
        metrics.tempWrittenBlocks += getNodeLong(node, "Temp Written Blocks");
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) collectMetricsRecursive(child, metrics, depth + 1);
        }
    }

    private void collectContextInfo(JsonNode node, AnalysisContext context) {
        if (node == null) return;
        String nodeType = getNodeText(node, "Node Type");
        if (nodeType.contains("Scan")) context.scanNodes.add(node);
        else if (nodeType.contains("Join")) context.joinNodes.add(node);
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) collectContextInfo(child, context);
        }
    }

    private double calculateIndexImprovement(long actualRows, long totalRows, String filter) {
        if (totalRows <= 0) totalRows = actualRows;
        if (actualRows == 0) return 50.0;
        double selectivity = (double) actualRows / totalRows;
        if (selectivity >= 0.3) return 30.0;
        double improvement = (1.0 - selectivity) * 100.0;
        return Math.min(95.0, Math.max(20.0, improvement));
    }

    private void addRecommendation(List<Recommendation> list, RecommendationType type, String desc, ImpactLevel impact, Double improvement, String sqlCmd) {
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
        switch (type) {
            case CREATE_INDEX: return "Создать индекс: CREATE INDEX ...;";
            case ADD_LIMIT: return "Добавить LIMIT: SELECT ... LIMIT N;";
            case INCREASE_WORK_MEM: return "Увеличить work_mem: SET work_mem = '64MB';";
            case UPDATE_STATISTICS: return "Обновить статистику: ANALYZE;";
            default: return "Проверьте план выполнения запроса.";
        }
    }

    private List<Recommendation> sortAndDeduplicate(List<Recommendation> recs) {
        Map<String, Recommendation> unique = new LinkedHashMap<>();
        for (Recommendation r : recs) {
            String key = r.getSqlCommand() != null ? r.getType() + ":" + r.getSqlCommand() : r.getType() + ":" + r.getDescription();
            unique.putIfAbsent(key, r);
        }
        List<Recommendation> list = new ArrayList<>(unique.values());
        list.sort((a, b) -> {
            int wa = getImpactWeight(a.getImpact());
            int wb = getImpactWeight(b.getImpact());
            if (wa != wb) return Integer.compare(wb, wa);
            double pa = a.getEstimatedImprovement() != null ? a.getEstimatedImprovement() : 0;
            double pb = b.getEstimatedImprovement() != null ? b.getEstimatedImprovement() : 0;
            return Double.compare(pb, pa);
        });
        return list.size() > 20 ? list.subList(0, 20) : list;
    }

    private void sortRecommendationsByImpact(List<Recommendation> recommendations) {
        recommendations.sort((a, b) -> {
            int wa = getImpactWeight(a.getImpact());
            int wb = getImpactWeight(b.getImpact());
            return Integer.compare(wb, wa);
        });
    }

    private int getImpactWeight(ImpactLevel level) {
        if (level == ImpactLevel.HIGH) return 3;
        if (level == ImpactLevel.MEDIUM) return 2;
        if (level == ImpactLevel.LOW) return 1;
        return 0;
    }

    private Recommendation createErrorRecommendation(String msg) {
        return Recommendation.builder()
                .type(RecommendationType.OTHER)
                .description(msg)
                .impact(ImpactLevel.HIGH)
                .estimatedImprovement(0.0)
                .applied(false)
                .warnings(List.of("Ошибка анализа"))
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