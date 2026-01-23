package com.pgoptima.analyticsservice.controller;

import com.pgoptima.analyticsservice.service.AnalyticsService;
import com.pgoptima.shareddto.request.AnalysisRequest;
import com.pgoptima.shareddto.response.AnalysisResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResponse> analyzeQuery(
            @Valid @RequestBody AnalysisRequest request) {
        AnalysisResponse response = analyticsService.analyzeQuery(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Analytics Service is UP");
    }
}