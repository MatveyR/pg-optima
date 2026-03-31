package com.pgoptima.shareddto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ValidateTokenRequest {
    @NotBlank
    private String token;
}