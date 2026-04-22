import apiClient from './client';
import {
    AnalysisRequest,
    AnalysisResponse,
    AsyncAnalysisResponse,
    ExecuteRequest,
    ExecuteResponse,
} from '../types/api.types';

export const analyticsApi = {
    // Анализ без авто-применения (бывший analyze-only)
    analyze: (data: AnalysisRequest) =>
        apiClient.post<AnalysisResponse>('/api/v1/optimization/analyze-only', data),

    // Асинхронный анализ
    submitAsync: (data: AnalysisRequest) =>
        apiClient.post<{ taskId: string }>('/api/v1/optimization/analyze-async', data),

    // Статус асинхронной задачи
    getAsyncStatus: (taskId: string) =>
        apiClient.get<AsyncAnalysisResponse>(`/api/v1/optimization/status/${taskId}`),

    // Выполнение запроса и получение результата (таблица)
    execute: (data: ExecuteRequest) =>
        apiClient.post<ExecuteResponse>('/api/v1/optimization/execute', data),

    // Статистика (заглушка)
    getStats: () =>
        apiClient.get<{
            total_analyses: number;
            successful_optimizations: number;
            average_improvement: number;
        }>('/api/v1/optimization/stats'),
};