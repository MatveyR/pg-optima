package com.pgoptima.analyticsservice.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PostgrePlanParser {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static JsonNode parse(String planJson) throws Exception {
        JsonNode root = mapper.readTree(planJson);
        // PostgreSQL возвращает массив с одним элементом
        return root.isArray() ? root.get(0).get("Plan") : root.get("Plan");
    }
}