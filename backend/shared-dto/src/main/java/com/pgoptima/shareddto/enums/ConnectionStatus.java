package com.pgoptima.shareddto.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Статус подключения к базе данных
 */
@Schema(description = "Статус подключения к базе данных")
public enum ConnectionStatus {

    @Schema(description = "Активно")
    ACTIVE("Активно"),

    @Schema(description = "Неактивно")
    INACTIVE("Неактивно"),

    @Schema(description = "Ошибка подключения")
    ERROR("Ошибка подключения"),

    @Schema(description = "Тестируется")
    TESTING("Тестируется"),

    @Schema(description = "Удалено")
    DELETED("Удалено");

    private final String displayName;

    ConnectionStatus(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    @JsonCreator
    public static ConnectionStatus fromString(String value) {
        if (value == null) return null;

        for (ConnectionStatus status : ConnectionStatus.values()) {
            if (status.name().equalsIgnoreCase(value) ||
                    status.displayName.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ConnectionStatus: " + value);
    }
}