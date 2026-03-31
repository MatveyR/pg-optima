package com.pgoptima.shareddto.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Статус асинхронной задачи")
public enum TaskStatus {
    PENDING("Ожидает"),
    RUNNING("Выполняется"),
    COMPLETED("Завершена"),
    FAILED("Ошибка");

    private final String description;

    TaskStatus(String description) {
        this.description = description;
    }

    @JsonValue
    public String getDescription() {
        return description;
    }

    @JsonCreator
    public static TaskStatus fromString(String value) {
        for (TaskStatus status : values()) {
            if (status.name().equalsIgnoreCase(value) || status.description.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown TaskStatus: " + value);
    }
}