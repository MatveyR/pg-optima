package com.pgoptima.shareddto.response;

import com.pgoptima.shareddto.enums.ImpactLevel;
import com.pgoptima.shareddto.enums.RecommendationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recommendation {
    private RecommendationType type;
    private String description;
    private ImpactLevel impact;
    private Double estimatedImprovement;
    private String sqlSuggestion;
    private String sqlCommand; // Конкретная SQL команда для применения
    private Boolean applied; // Была ли применена
    private Duration originalExecutionTime;
    private Duration optimizedExecutionTime;
    private Double actualImprovement; // Фактическое улучшение в %
    private Map<String, Object> metrics; // Дополнительные метрики
    private List<String> warnings; // Предупреждения при применении
}