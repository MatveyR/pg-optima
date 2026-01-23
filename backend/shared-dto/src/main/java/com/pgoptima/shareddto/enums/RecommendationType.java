package com.pgoptima.shareddto.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Типы рекомендаций по оптимизации SQL-запросов
 */
@Schema(description = "Типы рекомендаций по оптимизации SQL-запросов")
public enum RecommendationType {

    @Schema(description = "Создание индекса")
    CREATE_INDEX("Создание индекса"),

    @Schema(description = "Переписывание запроса")
    REWRITE_QUERY("Переписывание запроса"),

    @Schema(description = "Добавление LIMIT")
    ADD_LIMIT("Добавление LIMIT"),

    @Schema(description = "Очистка таблицы (VACUUM)")
    VACUUM_TABLE("Очистка таблицы"),

    @Schema(description = "Увеличение work_mem")
    INCREASE_WORK_MEM("Увеличение work_mem"),

    @Schema(description = "Обновление статистики")
    UPDATE_STATISTICS("Обновление статистики"),

    @Schema(description = "Использование временных таблиц")
    USE_TEMP_TABLE("Использование временных таблиц"),

    @Schema(description = "Изменение типа JOIN")
    CHANGE_JOIN_TYPE("Изменение типа JOIN"),

    @Schema(description = "Параллельное выполнение")
    ENABLE_PARALLEL("Параллельное выполнение"),

    @Schema(description = "Настройка параметров СУБД")
    TUNE_DB_PARAMS("Настройка параметров СУБД"),

    @Schema(description = "Разделение запроса")
    SPLIT_QUERY("Разделение запроса"),

    @Schema(description = "Кэширование результатов")
    CACHE_RESULTS("Кэширование результатов"),

    @Schema(description = "Другое")
    OTHER("Другое");

    private final String description;

    RecommendationType(String description) {
        this.description = description;
    }

    @JsonValue
    public String getDescription() {
        return description;
    }

    @JsonCreator
    public static RecommendationType fromString(String value) {
        if (value == null) return null;

        for (RecommendationType type : RecommendationType.values()) {
            if (type.name().equalsIgnoreCase(value) ||
                    type.description.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown RecommendationType: " + value);
    }

    /**
     * Возвращает рекомендации, связанные с индексами
     */
    public static RecommendationType[] getIndexRelatedTypes() {
        return new RecommendationType[]{CREATE_INDEX};
    }

    /**
     * Возвращает рекомендации, связанные с реструктуризацией запроса
     */
    public static RecommendationType[] getQueryRestructureTypes() {
        return new RecommendationType[]{REWRITE_QUERY, CHANGE_JOIN_TYPE, SPLIT_QUERY};
    }
}