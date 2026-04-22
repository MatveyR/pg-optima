package com.pgoptima.shareddto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExecuteRequest {
    @NotNull
    private Long connectionId;
    @NotBlank
    private String query;
}