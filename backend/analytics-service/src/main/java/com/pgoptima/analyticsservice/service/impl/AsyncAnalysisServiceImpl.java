package com.pgoptima.analyticsservice.service.impl;

import com.pgoptima.analyticsservice.dto.AnalysisTask;
import com.pgoptima.analyticsservice.service.AsyncAnalysisService;
import com.pgoptima.analyticsservice.service.AnalyticsService;
import com.pgoptima.shareddto.enums.TaskStatus;
import com.pgoptima.shareddto.request.AnalysisRequest;
import com.pgoptima.shareddto.response.AsyncAnalysisResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncAnalysisServiceImpl implements AsyncAnalysisService {

    private final AnalyticsService analyticsService;
    private final Map<String, AnalysisTask> tasks = new ConcurrentHashMap<>();

    @Override
    public String submitTask(AnalysisRequest request) {
        String taskId = UUID.randomUUID().toString();
        AnalysisTask task = new AnalysisTask();
        task.setTaskId(taskId);
        task.setStatus(TaskStatus.PENDING);
        task.setCreatedAt(Instant.now());
        tasks.put(taskId, task);
        processAsync(taskId, request);
        return taskId;
    }

    @Async("analysisExecutor")
    public void processAsync(String taskId, AnalysisRequest request) {
        AnalysisTask task = tasks.get(taskId);
        task.setStatus(TaskStatus.RUNNING);
        try {
            var response = analyticsService.analyzeQuery(request);
            task.setResult(response);
            task.setStatus(TaskStatus.COMPLETED);
        } catch (Exception e) {
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            log.error("Async analysis failed for task {}", taskId, e);
        }
        task.setCompletedAt(Instant.now());
    }

    @Override
    public AsyncAnalysisResponse getTaskStatus(String taskId) {
        AnalysisTask task = tasks.get(taskId);
        if (task == null) return null;
        return AsyncAnalysisResponse.builder()
                .taskId(task.getTaskId())
                .status(task.getStatus())
                .createdAt(task.getCreatedAt())
                .completedAt(task.getCompletedAt())
                .result(task.getResult())
                .errorMessage(task.getErrorMessage())
                .build();
    }
}