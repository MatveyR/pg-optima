import apiClient from './client';
import {
    ProjectDTO,
    CreateProjectRequest,
    SavedQueryDTO,
    SaveQueryRequest,
} from '../types/api.types';

export const projectsApi = {
    getAll: () => apiClient.get<ProjectDTO[]>('/api/v1/projects'),
    getById: (id: number) => apiClient.get<ProjectDTO>(`/api/v1/projects/${id}`),
    create: (data: CreateProjectRequest) =>
        apiClient.post<ProjectDTO>('/api/v1/projects', data),
    delete: (id: number) => apiClient.delete(`/api/v1/projects/${id}`),
    getQueries: (projectId: number) =>
        apiClient.get<SavedQueryDTO[]>(`/api/v1/projects/${projectId}/queries`),
    saveQuery: (data: SaveQueryRequest) =>
        apiClient.post<SavedQueryDTO>('/api/v1/projects/queries', data),
    deleteQuery: (queryId: number) =>
        apiClient.delete(`/api/v1/projects/queries/${queryId}`),
};