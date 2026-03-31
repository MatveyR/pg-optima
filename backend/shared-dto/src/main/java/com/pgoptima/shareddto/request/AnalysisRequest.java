package com.pgoptima.shareddto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisRequest {

    @NotNull(message = "Connection ID is required")
    private Long connectionId;

    @NotBlank(message = "SQL query cannot be empty")
    private String sqlQuery;

    private boolean autoApply = false;

    private Integer timeoutSeconds = 30;

    private boolean includeStatistics = true;
}