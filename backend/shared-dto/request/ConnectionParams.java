package com.pgoptima.analytics.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

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