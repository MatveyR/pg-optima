package com.pgoptima.shareddto.response;

import com.pgoptima.shareddto.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncAnalysisResponse {
    private String taskId;
    private TaskStatus status;
    private Instant createdAt;
    private Instant completedAt;
    private AnalysisResponse result;
    private String errorMessage;
}