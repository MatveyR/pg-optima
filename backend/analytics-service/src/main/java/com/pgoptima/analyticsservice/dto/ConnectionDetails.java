package com.pgoptima.analyticsservice.dto;

import lombok.Data;

@Data
public class ConnectionDetails {
    private Long id;
    private String name;
    private String host;
    private int port;
    private String database;
    private String username;
    private String password;   // пароль будет расшифрован user-service
    private String sslMode;
    private Long ownerId;
}