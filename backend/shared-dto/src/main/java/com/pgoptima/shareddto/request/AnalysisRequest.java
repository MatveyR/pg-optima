package com.pgoptima.shareddto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Запрос на анализ SQL-запроса")
public class AnalysisRequest {

    @NotBlank(message = "SQL query cannot be blank")
    @Schema(description = "SQL запрос для анализа", example = "SELECT * FROM users WHERE age > 30")
    private String sqlQuery;

    @NotNull(message = "Connection parameters are required")
    @Schema(description = "Параметры подключения к БД")
    private ConnectionParams connection;

    @Schema(description = "Таймаут выполнения в секундах", example = "30")
    private Integer timeoutSeconds = 30;

    @Schema(description = "Включить анализ буферов", example = "true")
    private boolean analyzeBuffers = true;
}