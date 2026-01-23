package com.pgoptima.dto.response;

import com.pgoptima.dto.enums.ImpactLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResponse {
    private boolean success;
    private String originalQuery;
    private String executionPlanJson;
    private List<Recommendation> recommendations;
    private Duration executionTime;
    private String errorMessage;

    // Методы-помощники
    public boolean hasHighImpactRecommendations() {
        return recommendations.stream()
                .anyMatch(r -> ImpactLevel.HIGH.equals(r.getImpact()));
    }
}