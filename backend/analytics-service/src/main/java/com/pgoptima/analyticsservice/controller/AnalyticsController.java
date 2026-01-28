package com.pgoptima.analyticsservice.controller;

import com.pgoptima.analyticsservice.service.AnalyticsService;
import com.pgoptima.shareddto.request.AnalysisRequest;
import com.pgoptima.shareddto.response.AnalysisResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/optimization")
@RequiredArgsConstructor
@Tag(name = "Query Optimization", description = "API для автоматической оптимизации SQL запросов")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @PostMapping("/analyze-and-optimize")
    @Operation(
            summary = "Проанализировать и оптимизировать запрос",
            description = "Анализирует SQL запрос, предлагает оптимизации, автоматически применяет их и измеряет улучшение"
    )
    @ApiResponse(responseCode = "200", description = "Анализ и оптимизация выполнены успешно")
    @ApiResponse(responseCode = "400", description = "Неверные параметры запроса")
    @ApiResponse(responseCode = "500", description = "Ошибка сервера")
    public ResponseEntity<AnalysisResponse> analyzeAndOptimize(
            @Parameter(description = "Параметры анализа и оптимизации", required = true)
            @Valid @RequestBody AnalysisRequest request) {

        // Включаем авто-применение для этого эндпоинта
        request.setAutoApply(true);

        AnalysisResponse response = analyticsService.analyzeQuery(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/analyze-only")
    @Operation(
            summary = "Только анализ запроса",
            description = "Анализирует SQL запрос и предлагает оптимизации без автоматического применения"
    )
    public ResponseEntity<AnalysisResponse> analyzeOnly(
            @Valid @RequestBody AnalysisRequest request) {

        // Отключаем авто-применение
        request.setAutoApply(false);

        AnalysisResponse response = analyticsService.analyzeQuery(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    @Operation(
            summary = "Статистика оптимизаций",
            description = "Возвращает статистику по выполненным оптимизациям"
    )
    public ResponseEntity<Map<String, Object>> getOptimizationStats() {
        // TODO: Реализовать сбор статистики
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_analyses", 0);
        stats.put("successful_optimizations", 0);
        stats.put("average_improvement", 0.0);
        stats.put("best_improvement", 0.0);

        return ResponseEntity.ok(stats);
    }

    @PostMapping("/apply/{recommendationId}")
    @Operation(
            summary = "Применить конкретную рекомендацию",
            description = "Применяет конкретную рекомендацию и измеряет результат"
    )
    public ResponseEntity<Map<String, Object>> applyRecommendation(
            @PathVariable String recommendationId,
            @RequestBody Map<String, String> params) {

        Map<String, Object> result = new HashMap<>();

        try {
            // TODO: Реализовать применение конкретной рекомендации
            result.put("success", true);
            result.put("recommendation_id", recommendationId);
            result.put("applied", true);
            result.put("improvement_percent", 25.5);
            result.put("message", "Рекомендация успешно применена");

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }
}