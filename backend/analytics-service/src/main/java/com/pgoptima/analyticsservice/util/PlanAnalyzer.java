package com.pgoptima.analyticsservice.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.pgoptima.shareddto.enums.ImpactLevel;
import com.pgoptima.shareddto.enums.RecommendationType;
import com.pgoptima.shareddto.response.Recommendation;

import java.util.*;

public class PlanAnalyzer {

    public static List<Recommendation> analyze(JsonNode plan) {
        List<Recommendation> recommendations = new ArrayList<>();

        analyzeNode(plan, recommendations);
        return recommendations;
    }

    private static void analyzeNode(JsonNode node, List<Recommendation> recommendations) {
        if (node == null) return;

        String nodeType = node.get("Node Type").asText();

        // 1. Проверка Seq Scan с большим количеством строк
        if ("Seq Scan".equals(nodeType)) {
            long rowsRemoved = node.path("Rows Removed by Filter").asLong();
            long actualRows = node.path("Actual Rows").asLong();

            if (actualRows > 1000) {
                String tableName = node.path("Relation Name").asText();
                Recommendation rec = new Recommendation();
                rec.setType(RecommendationType.CREATE_INDEX);
                rec.setDescription(String.format(
                        "Seq Scan on table '%s' processed %d rows. Consider adding an index.",
                        tableName, actualRows));
                rec.setImpact(ImpactLevel.valueOf("MEDIUM"));
                recommendations.add(rec);
            }
        }

        // 2. Проверка сортировки с внешней памятью
        if ("Sort".equals(nodeType)) {
            String sortSpaceType = node.path("Sort Space Type").asText();
            if ("Disk".equals(sortSpaceType)) {
                Recommendation rec = new Recommendation();
                rec.setType(RecommendationType.INCREASE_WORK_MEM);
                rec.setDescription("Sort operation used disk. Increase work_mem parameter.");
                rec.setImpact(ImpactLevel.valueOf("MEDIUM"));
                recommendations.add(rec);
            }
        }

        // 3. Проверка на отсутствие индексов в условиях
        if (node.has("Filter")) {
            String filter = node.get("Filter").asText();
            if (filter.contains("IS NOT NULL") || filter.contains("=")) {
                // Простая эвристика: если есть фильтр, но нет индекса
                if (!nodeType.contains("Index")) {
                    Recommendation rec = new Recommendation();
                    rec.setType(RecommendationType.CREATE_INDEX);
                    rec.setDescription("Filter condition without index usage detected.");
                    rec.setImpact(ImpactLevel.valueOf("HIGH"));
                    recommendations.add(rec);
                }
            }
        }

        // Рекурсивный обход дочерних узлов
        JsonNode plans = node.get("Plans");
        if (plans != null && plans.isArray()) {
            for (JsonNode child : plans) {
                analyzeNode(child, recommendations);
            }
        }
    }
}