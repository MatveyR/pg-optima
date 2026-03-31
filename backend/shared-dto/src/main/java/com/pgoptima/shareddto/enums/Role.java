package com.pgoptima.shareddto.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Роль пользователя")
public enum Role {
    USER("Пользователь"),
    ADMIN("Администратор");

    private final String description;

    Role(String description) {
        this.description = description;
    }

    @JsonValue
    public String getDescription() {
        return description;
    }

    @JsonCreator
    public static Role fromString(String value) {
        for (Role role : values()) {
            if (role.name().equalsIgnoreCase(value) || role.description.equalsIgnoreCase(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + value);
    }
}