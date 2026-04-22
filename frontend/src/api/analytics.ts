import apiClient from './client';
import {
    AnalysisRequest,
    AnalysisResponse,
    AsyncAnalysisResponse,
} from '../types/api.types';

export const analyticsApi = {
    analyzeAndOptimize: (data: AnalysisRequest) =>
        apiClient.post<AnalysisResponse>('/api/v1/optimization/analyze-and-optimize', data),
    analyzeOnly: (data: AnalysisRequest) =>
        apiClient.post<AnalysisResponse>('/api/v1/optimization/analyze-only', data),
    submitAsync: (data: AnalysisRequest) =>
        apiClient.post<{ taskId: string }>('/api/v1/optimization/analyze-async', data),
    getAsyncStatus: (taskId: string) =>
        apiClient.get<AsyncAnalysisResponse>(`/api/v1/optimization/status/${taskId}`),
    getStats: () => apiClient.get<{
        total_analyses: number;
        successful_optimizations: number;
        average_improvement: number;
    }>('/api/v1/optimization/stats'),
};