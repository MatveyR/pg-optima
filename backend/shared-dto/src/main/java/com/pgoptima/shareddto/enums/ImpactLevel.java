package com.pgoptima.shareddto.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Уровень влияния рекомендации на производительность
 */
@Schema(description = "Уровень влияния рекомендации на производительность")
public enum ImpactLevel {

    @Schema(description = "Высокий - значительное улучшение производительности (>50%)")
    HIGH("Высокий", 0.5),

    @Schema(description = "Средний - заметное улучшение производительности (20-50%)")
    MEDIUM("Средний", 0.2),

    @Schema(description = "Низкий - незначительное улучшение (<20%)")
    LOW("Низкий", 0.0),

    @Schema(description = "Информационный - рекомендация без прямой оценки улучшения")
    INFO("Информационный", 0.0);

    private final String description;
    private final double minImprovementThreshold;

    ImpactLevel(String description, double minImprovementThreshold) {
        this.description = description;
        this.minImprovementThreshold = minImprovementThreshold;
    }

    @JsonValue
    public String getDescription() {
        return description;
    }

    public double getMinImprovementThreshold() {
        return minImprovementThreshold;
    }

    @JsonCreator
    public static ImpactLevel fromString(String value) {
        if (value == null) return null;

        for (ImpactLevel level : ImpactLevel.values()) {
            if (level.name().equalsIgnoreCase(value) ||
                    level.description.equalsIgnoreCase(value)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown ImpactLevel: " + value);
    }

    /**
     * Возвращает уровень влияния на основе предполагаемого улучшения в процентах
     */
    public static ImpactLevel fromImprovementPercentage(double improvementPercent) {
        if (improvementPercent >= HIGH.minImprovementThreshold) {
            return HIGH;
        } else if (improvementPercent >= MEDIUM.minImprovementThreshold) {
            return MEDIUM;
        } else if (improvementPercent > 0) {
            return LOW;
        } else {
            return INFO;
        }
    }
}