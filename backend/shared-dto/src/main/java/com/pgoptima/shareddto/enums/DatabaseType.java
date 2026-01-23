package com.pgoptima.shareddto.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * Поддерживаемые типы баз данных
 */
@Schema(description = "Поддерживаемые типы баз данных")
public enum DatabaseType {

    @Schema(description = "PostgreSQL")
    POSTGRESQL("PostgreSQL", "org.postgresql.Driver", "jdbc:postgresql://{host}:{port}/{database}");

    private final String displayName;
    private final String driverClassName;
    @Getter
    private final String jdbcUrlTemplate;

    DatabaseType(String displayName, String driverClassName, String jdbcUrlTemplate) {
        this.displayName = displayName;
        this.driverClassName = driverClassName;
        this.jdbcUrlTemplate = jdbcUrlTemplate;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Формирует JDBC URL на основе параметров
     */
    public String buildJdbcUrl(String host, int port, String database) {
        return jdbcUrlTemplate
                .replace("{host}", host)
                .replace("{port}", String.valueOf(port))
                .replace("{database}", database);
    }

    @JsonCreator
    public static DatabaseType fromString(String value) {
        if (value == null) return null;

        for (DatabaseType type : DatabaseType.values()) {
            if (type.name().equalsIgnoreCase(value) ||
                    type.displayName.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown DatabaseType: " + value);
    }
    }