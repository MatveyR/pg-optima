package com.pgoptima.shareddto.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

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

    // НОВЫЕ ТИПЫ РЕКОМЕНДАЦИЙ
    @Schema(description = "Замена коррелированных подзапросов на SEMI JOIN")
    REWRITE_JOIN_TO_SEMI_JOIN("Замена коррелированных подзапросов на SEMI JOIN"),

    @Schema(description = "Исправление неявного приведения типов")
    FIX_DATA_TYPE_MISMATCH("Исправление неявного приведения типов"),

    @Schema(description = "Включение параллельных запросов")
    ENABLE_PARALLEL_QUERY("Включение параллельных запросов"),

    @Schema(description = "Партиционирование больших таблиц")
    SUGGEST_PARTITIONING("Партиционирование больших таблиц"),

    @Schema(description = "JIT-компиляция для сложных запросов")
    ENABLE_JIT_COMPILATION("JIT-компиляция для сложных запросов"),

    @Schema(description = "Оптимизация GROUP BY (индекс/материализованное представление)")
    OPTIMIZE_GROUP_BY("Оптимизация GROUP BY"),

    @Schema(description = "Избегать функций в условии WHERE")
    AVOID_FUNCTION_IN_WHERE("Избегать функций в WHERE"),

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

    public static RecommendationType[] getIndexRelatedTypes() {
        return new RecommendationType[]{CREATE_INDEX};
    }

    public static RecommendationType[] getQueryRestructureTypes() {
        return new RecommendationType[]{REWRITE_QUERY, CHANGE_JOIN_TYPE, SPLIT_QUERY, REWRITE_JOIN_TO_SEMI_JOIN};
    }

    /**
     * Типы рекомендаций, связанные с настройкой параметров СУБД
     */
    public static RecommendationType[] getParameterTuningTypes() {
        return new RecommendationType[]{INCREASE_WORK_MEM, ENABLE_PARALLEL_QUERY, ENABLE_JIT_COMPILATION, TUNE_DB_PARAMS};
    }

    /**
     * Типы рекомендаций по изменению схемы БД
     */
    public static RecommendationType[] getSchemaChangeTypes() {
        return new RecommendationType[]{CREATE_INDEX, SUGGEST_PARTITIONING, FIX_DATA_TYPE_MISMATCH};
    }
}