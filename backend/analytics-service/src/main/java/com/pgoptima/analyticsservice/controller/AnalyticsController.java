package com.pgoptima.analyticsservice.controller;

import com.pgoptima.analyticsservice.service.AnalyticsService;
import com.pgoptima.analyticsservice.service.AsyncAnalysisService;
import com.pgoptima.shareddto.request.AnalysisRequest;
import com.pgoptima.shareddto.response.AnalysisResponse;
import com.pgoptima.shareddto.response.AsyncAnalysisResponse;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Query Optimization")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final AsyncAnalysisService asyncAnalysisService;

    @PostMapping("/analyze-and-optimize")
    @Operation(summary = "Analyze and optimize query (synchronous)")
    public ResponseEntity<AnalysisResponse> analyzeAndOptimize(@Valid @RequestBody AnalysisRequest request) {
        request.setAutoApply(true);
        return ResponseEntity.ok(analyticsService.analyzeQuery(request));
    }

    @PostMapping("/analyze-only")
    @Operation(summary = "Analyze only (no auto-apply)")
    public ResponseEntity<AnalysisResponse> analyzeOnly(@Valid @RequestBody AnalysisRequest request) {
        request.setAutoApply(false);
        return ResponseEntity.ok(analyticsService.analyzeQuery(request));
    }

    @PostMapping("/analyze-async")
    @Operation(summary = "Submit async analysis task")
    public ResponseEntity<Map<String, String>> submitAsync(@Valid @RequestBody AnalysisRequest request) {
        String taskId = asyncAnalysisService.submitTask(request);
        return ResponseEntity.ok(Map.of("taskId", taskId));
    }

    @GetMapping("/status/{taskId}")
    @Operation(summary = "Get async task status")
    public ResponseEntity<AsyncAnalysisResponse> getStatus(@PathVariable String taskId) {
        AsyncAnalysisResponse resp = asyncAnalysisService.getTaskStatus(taskId);
        if (resp == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/stats")
    @Operation(summary = "Optimization statistics")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_analyses", 0);
        stats.put("successful_optimizations", 0);
        stats.put("average_improvement", 0.0);
        return ResponseEntity.ok(stats);
    }
}