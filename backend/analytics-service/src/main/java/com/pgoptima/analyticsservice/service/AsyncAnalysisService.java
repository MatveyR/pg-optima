package com.pgoptima.analyticsservice.service;

import com.pgoptima.shareddto.request.AnalysisRequest;
import com.pgoptima.shareddto.response.AsyncAnalysisResponse;

public interface AsyncAnalysisService {
    String submitTask(AnalysisRequest request);
    AsyncAnalysisResponse getTaskStatus(String taskId);
}