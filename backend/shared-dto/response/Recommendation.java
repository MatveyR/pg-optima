package com.pgoptima.dto.response;

import com.pgoptima.dto.enums.ImpactLevel;
import com.pgoptima.dto.enums.RecommendationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recommendation {
    private RecommendationType type;
    private String description;
    private String sqlSuggestion;
    private ImpactLevel impact;
    private Double estimatedImprovement; // в процентах
}