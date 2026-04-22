package com.pgoptima.analyticsservice.controller;

import com.pgoptima.analyticsservice.service.AnalyticsService;
import com.pgoptima.analyticsservice.service.AsyncAnalysisService;
import com.pgoptima.shareddto.request.AnalysisRequest;
import com.pgoptima.shareddto.request.ExecuteRequest;
import com.pgoptima.shareddto.response.AnalysisResponse;
import com.pgoptima.shareddto.response.AsyncAnalysisResponse;
import com.pgoptima.shareddto.response.ExecuteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/optimization")
@RequiredArgsConstructor
@Tag(name = "Query Optimization")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final AsyncAnalysisService asyncAnalysisService;

    @PostMapping("/analyze-only")
    @Operation(summary = "Analyze query (no auto-apply)")
    public ResponseEntity<AnalysisResponse> analyzeOnly(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody AnalysisRequest request) {
        request.setAutoApply(false);
        return ResponseEntity.ok(analyticsService.analyzeQuery(request, authHeader));
    }

    @PostMapping("/analyze-async")
    @Operation(summary = "Submit async analysis task")
    public ResponseEntity<Map<String, String>> submitAsync(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody AnalysisRequest request) {
        String taskId = asyncAnalysisService.submitTask(request, authHeader);
        return ResponseEntity.ok(Map.of("taskId", taskId));
    }

    @GetMapping("/status/{taskId}")
    @Operation(summary = "Get async task status")
    public ResponseEntity<AsyncAnalysisResponse> getStatus(@PathVariable String taskId) {
        AsyncAnalysisResponse resp = asyncAnalysisService.getTaskStatus(taskId);
        if (resp == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/execute")
    @Operation(summary = "Execute query and return results")
    public ResponseEntity<ExecuteResponse> execute(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody ExecuteRequest request) {
        return ResponseEntity.ok(analyticsService.executeQuery(request, authHeader));
    }

    @GetMapping("/stats")
    @Operation(summary = "Optimization statistics")
    public ResponseEntity<Map<String, Object>> getStats() {
        // Заглушка, можно реализовать позже
        return ResponseEntity.ok(Map.of(
                "total_analyses", 0,
                "successful_optimizations", 0,
                "average_improvement", 0.0
        ));
    }
}