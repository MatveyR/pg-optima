package com.pgoptima.analyticsservice.service;

import com.pgoptima.shareddto.request.AnalysisRequest;
import com.pgoptima.shareddto.response.AnalysisResponse;

public interface AnalyticsService {
    AnalysisResponse analyzeQuery(AnalysisRequest request);
}