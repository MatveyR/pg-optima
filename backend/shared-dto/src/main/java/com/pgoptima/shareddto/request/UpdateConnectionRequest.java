package com.pgoptima.shareddto.request;

import com.pgoptima.shareddto.enums.DatabaseType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdateConnectionRequest {
    private String name;
    private DatabaseType databaseType;
    private String host;
    @Min(1) @Max(65535)
    private Integer port;
    private String database;
    private String username;
    private String password;
    private String sslMode;
}