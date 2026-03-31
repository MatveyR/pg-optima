-- История оптимизаций
CREATE TABLE IF NOT EXISTS optimization_history (
                                                    id BIGSERIAL PRIMARY KEY,
                                                    user_id BIGINT NOT NULL,
                                                    connection_id BIGINT NOT NULL,
                                                    original_query TEXT NOT NULL,
                                                    original_execution_time_ms BIGINT,
                                                    optimized_execution_time_ms BIGINT,
                                                    improvement_percent DOUBLE PRECISION,
                                                    success BOOLEAN NOT NULL DEFAULT TRUE,
                                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Результаты рекомендаций
CREATE TABLE IF NOT EXISTS recommendation_results (
                                                      id BIGSERIAL PRIMARY KEY,
                                                      history_id BIGINT NOT NULL REFERENCES optimization_history(id) ON DELETE CASCADE,
                                                      type VARCHAR(50) NOT NULL,
                                                      description TEXT NOT NULL,
                                                      sql_command TEXT,
                                                      applied BOOLEAN,
                                                      estimated_improvement DOUBLE PRECISION,
                                                      actual_improvement DOUBLE PRECISION,
                                                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);