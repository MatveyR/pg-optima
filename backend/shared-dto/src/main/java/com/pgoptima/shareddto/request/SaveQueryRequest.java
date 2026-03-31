package com.pgoptima.shareddto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SaveQueryRequest {
    @NotNull
    private Long projectId;

    @NotBlank
    private String name;

    @NotBlank
    private String sqlQuery;

    private String description;
}