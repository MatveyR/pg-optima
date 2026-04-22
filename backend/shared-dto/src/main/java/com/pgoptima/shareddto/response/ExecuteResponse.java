package com.pgoptima.shareddto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ExecuteResponse {
    private List<String> columns;
    private List<List<Object>> rows;
    private int rowCount;
    private long executionTimeMs;
    private boolean success;
    private String errorMessage;
}