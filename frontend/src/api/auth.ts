import apiClient from './client';
import {
    LoginRequest,
    LoginResponse,
    RegisterRequest,
    UserDTO,
    ValidateTokenRequest,
} from '../types/api.types';

export const authApi = {
    register: (data: RegisterRequest) =>
        apiClient.post<UserDTO>('/api/v1/auth/register', data),
    login: (data: LoginRequest) =>
        apiClient.post<LoginResponse>('/api/v1/auth/login', data),
    refreshToken: (refreshToken: string) =>
        apiClient.post<LoginResponse>('/api/v1/auth/refresh', { refreshToken }),
    validateToken: (token: string) =>
        apiClient.post<{ valid: boolean }>('/api/v1/auth/validate', { token } as ValidateTokenRequest),
};