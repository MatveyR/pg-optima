import apiClient from './client';
import {
    ConnectionDTO,
    CreateConnectionRequest,
    UpdateConnectionRequest,
} from '../types/api.types';

export const connectionsApi = {
    getAll: () => apiClient.get<ConnectionDTO[]>('/api/v1/connections'),
    getById: (id: number) => apiClient.get<ConnectionDTO>(`/api/v1/connections/${id}`),
    create: (data: CreateConnectionRequest) =>
        apiClient.post<ConnectionDTO>('/api/v1/connections', data),
    update: (id: number, data: UpdateConnectionRequest) =>
        apiClient.put<ConnectionDTO>(`/api/v1/connections/${id}`, data),
    delete: (id: number) => apiClient.delete(`/api/v1/connections/${id}`),
    test: (data: CreateConnectionRequest) =>
        apiClient.post<{ success: boolean }>('/api/v1/connections/test', data),
};