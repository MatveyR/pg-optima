package com.pgoptima.shareddto.request;

import com.pgoptima.shareddto.enums.DatabaseType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateConnectionRequest {
    @NotBlank
    private String name;

    @NotNull
    private DatabaseType databaseType = DatabaseType.POSTGRESQL;

    @NotBlank
    private String host;

    @Min(1) @Max(65535)
    private int port = 5432;

    @NotBlank
    private String database;

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    private String sslMode = "disable";
}