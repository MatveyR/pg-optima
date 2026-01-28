package com.pgoptima.shareddto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisRequest {
    @NotBlank(message = "SQL query is required")
    private String sqlQuery;

    @Valid
    @NotNull(message = "Database connection is required")
    private ConnectionParams connection;

    private boolean autoApply = false; // Флаг авто-применения рекомендаций
    private int timeoutSeconds = 30; // Таймаут выполнения
    private boolean includeStatistics = true; // Включать ли статистику
}