package com.pgoptima.shareddto.response;

import com.pgoptima.shareddto.response.Recommendation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResponse {
    private boolean success;
    private String errorMessage;
    private String originalQuery;
    private String executionPlanJson;
    private Duration originalExecutionTime;
    private Duration analysisDuration;
    private Instant requestTimestamp;
    private List<Recommendation> recommendations;
    private Map<String, Object> optimizationStatistics;
    private String optimizationReport;
}