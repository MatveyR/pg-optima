package com.pgoptima.analyticsservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SlowQueryDTO {
    private String query;
    private double meanExecutionTimeMs;
    private long calls;
    private long rows;
    private String lastExecuted;
}