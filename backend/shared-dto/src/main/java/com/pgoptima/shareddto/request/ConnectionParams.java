package com.pgoptima.shareddto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConnectionParams {
    @NotBlank
    private String host;
    private int port = 5432;
    @NotBlank
    private String database;
    @NotBlank
    private String username;
    @NotBlank
    private String password;
    private String sslMode = "disable";
}