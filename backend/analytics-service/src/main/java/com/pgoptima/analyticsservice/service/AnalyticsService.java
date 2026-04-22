package com.pgoptima.analyticsservice.service;

import com.pgoptima.shareddto.request.AnalysisRequest;
import com.pgoptima.shareddto.request.ExecuteRequest;
import com.pgoptima.shareddto.response.AnalysisResponse;
import com.pgoptima.shareddto.response.ExecuteResponse;

public interface AnalyticsService {
    AnalysisResponse analyzeQuery(AnalysisRequest request, String authHeader);
    ExecuteResponse executeQuery(ExecuteRequest request, String authHeader);
}