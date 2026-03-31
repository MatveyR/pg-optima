package com.pgoptima.shareddto.response;

import com.pgoptima.shareddto.enums.ConnectionStatus;
import com.pgoptima.shareddto.enums.DatabaseType;
import lombok.Data;

import java.time.Instant;

@Data
public class ConnectionDTO {
    private Long id;
    private String name;
    private DatabaseType databaseType;
    private String host;
    private int port;
    private String database;
    private String username;
    private String sslMode;
    private ConnectionStatus status;
    private Long ownerId;
    private Instant createdAt;
    private Instant updatedAt;
    private String password;
}