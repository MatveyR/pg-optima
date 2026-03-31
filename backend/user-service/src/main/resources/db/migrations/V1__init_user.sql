-- Подключения
CREATE TABLE IF NOT EXISTS connections (
                                           id BIGSERIAL PRIMARY KEY,
                                           name VARCHAR(255) NOT NULL,
                                           database_type VARCHAR(50) NOT NULL DEFAULT 'POSTGRESQL',
                                           host VARCHAR(255) NOT NULL,
                                           port INT NOT NULL DEFAULT 5432,
                                           database VARCHAR(255) NOT NULL,
                                           username VARCHAR(255) NOT NULL,
                                           encrypted_password TEXT NOT NULL,
                                           ssl_mode VARCHAR(50) DEFAULT 'disable',
                                           status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
                                           owner_id BIGINT NOT NULL,
                                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Проекты
CREATE TABLE IF NOT EXISTS projects (
                                        id BIGSERIAL PRIMARY KEY,
                                        name VARCHAR(255) NOT NULL,
                                        description TEXT,
                                        owner_id BIGINT NOT NULL,
                                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Сохранённые запросы
CREATE TABLE IF NOT EXISTS saved_queries (
                                             id BIGSERIAL PRIMARY KEY,
                                             name VARCHAR(255) NOT NULL,
                                             sql_query TEXT NOT NULL,
                                             description TEXT,
                                             project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
                                             created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                             updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);