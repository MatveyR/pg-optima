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
    fullName: string;
    email: string;
    role?: string;
    createdAt?: string;
}

export interface LoginResponse {
    accessToken: string;
    refreshToken: string;
    tokenType?: string;
    expiresIn?: number;
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
    password?: string;
    sslMode?: string;
    status?: string;
    ownerId?: number;
    createdAt?: string;
    updatedAt?: string;
}

export interface CreateConnectionRequest {
    name: string;
    host: string;
    port: number;
    database: string;
    username: string;
    password: string;
    sslMode?: string;
    databaseType?: string;
}

export type UpdateConnectionRequest = Partial<CreateConnectionRequest>;

// Проекты и сохранённые запросы
export interface ProjectDTO {
    id: number;
    name: string;
    description?: string;
    ownerId?: number;
    createdAt?: string;
    savedQueries?: SavedQueryDTO[];
}

export interface CreateProjectRequest {
    name: string;
    description?: string;
}

export interface SavedQueryDTO {
    id: number;
    name: string;
    sqlQuery: string;
    description?: string;
    projectId: number;
    createdAt?: string;
    updatedAt?: string;
}

export interface SaveQueryRequest {
    projectId: number;
    name: string;
    sqlQuery: string;
    description?: string;
}

export interface AnalysisRequest {
    connectionId: number;
    sqlQuery: string;
    autoApply?: boolean;
    timeoutSeconds?: number;
    includeStatistics?: boolean;
    iterations?: number;
    applyRecommendations?: boolean;
}

export interface Recommendation {
    type: string;
    description: string;
    impact: 'Высокий' | 'Средний' | 'Низкий' | 'Информационный';
    estimatedImprovement?: number;
    actualImprovement?: number;
    sqlSuggestion?: string;
    sqlCommand?: string;
    applied?: boolean;
    warnings?: string[];
    metrics?: Record<string, any>;
}

export interface AnalysisResponse {
    success: boolean;
    errorMessage?: string;
    originalQuery: string;
    executionPlanJson?: string;
    originalExecutionTimeMs: number;
    analysisDurationMs: number;
    requestTimestamp?: string;
    recommendations: Recommendation[];
    optimizationStatistics?: Record<string, any>;
    optimizationReport?: string;
}

export interface AsyncAnalysisResponse {
    taskId: string;
    status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
    createdAt?: string;
    completedAt?: string;
    result?: AnalysisResponse;
    errorMessage?: string;
}

export interface ExecuteRequest {
    connectionId: number;
    query: string;
    iterations?: number;
}

export interface ExecuteResponse {
    columns: string[];
    rows: any[][];
    rowCount: number;
    executionTimeMs: number;
    success: boolean;
    errorMessage?: string;
}