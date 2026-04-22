// Аутентификация
export interface LoginRequest {
    email: string;
    password: string;
}

export interface RegisterRequest {
    fullName: string;
    email: string;
    password: string;
}

export interface UserDTO {
    id: number;
    name: string;
    email: string;
    createdAt: string;
}

export interface LoginResponse {
    accessToken: string;
    refreshToken: string;
    user: UserDTO;
}

export interface ValidateTokenRequest {
    token: string;
}

// Подключения
export interface ConnectionDTO {
    id: number;
    name: string;
    host: string;
    port: number;
    database: string;
    username: string;
    createdAt: string;
}

export interface CreateConnectionRequest {
    name: string;
    host: string;
    port: number;
    database: string;
    username: string;
    password: string;
    sslMode?: 'disable' | 'prefer' | 'require';
}

export type UpdateConnectionRequest = Partial<CreateConnectionRequest>;

// Проекты и сохранённые запросы
export interface ProjectDTO {
    id: number;
    name: string;
    description?: string;
    createdAt: string;
    updatedAt: string;
}

export interface CreateProjectRequest {
    name: string;
    description?: string;
}

export interface SavedQueryDTO {
    id: number;
    projectId: number;
    name: string;
    sqlText: string;
    createdAt: string;
}

export interface SaveQueryRequest {
    projectId: number;
    name: string;
    sqlText: string;
}

// Анализ запросов
export interface AnalysisRequest {
    connectionId: number;
    query: string;
    autoApply?: boolean;
}

export interface AnalysisResponse {
    originalQuery: string;
    optimizedQuery: string;
    executionTimeMs: number;
    estimatedImprovementPercent: number;
    issues: Array<{
        severity: 'high' | 'medium' | 'low';
        title: string;
        description: string;
        suggestion: string;
    }>;
}

export interface AsyncAnalysisResponse {
    taskId: string;
    status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
    result?: AnalysisResponse;
    error?: string;
}