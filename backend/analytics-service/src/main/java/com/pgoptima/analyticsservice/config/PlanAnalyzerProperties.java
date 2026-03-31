package com.pgoptima.analyticsservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "pgoptima.analysis")
public class PlanAnalyzerProperties {
    private long seqScanThreshold = 1000;
    private double highCostRatio = 0.3;
    private long highBufferThreshold = 10000;
    private double parallelEffectivenessThreshold = 1.5;
    private long indexScanRowThreshold = 10000;
    private double cacheHitRatioThreshold = 0.8;
    private long largeJoinThreshold = 100000;
    private long highExecutionTimeMs = 5000;
    private int maxJoinCount = 5;
    private boolean allowModifyingQueries = false;
}