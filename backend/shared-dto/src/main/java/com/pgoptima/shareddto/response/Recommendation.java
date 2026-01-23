package com.pgoptima.shareddto.response;

import com.pgoptima.shareddto.enums.ImpactLevel;
import com.pgoptima.shareddto.enums.RecommendationType;
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