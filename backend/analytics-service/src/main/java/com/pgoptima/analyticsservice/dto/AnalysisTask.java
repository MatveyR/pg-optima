package com.pgoptima.analyticsservice.dto;

import com.pgoptima.shareddto.enums.TaskStatus;
import com.pgoptima.shareddto.response.AnalysisResponse;
import lombok.Data;

import java.time.Instant;

@Data
public class AnalysisTask {
    private String taskId;
    private TaskStatus status;
    private Instant createdAt;
    private Instant completedAt;
    private AnalysisResponse result;
    private String errorMessage;
}