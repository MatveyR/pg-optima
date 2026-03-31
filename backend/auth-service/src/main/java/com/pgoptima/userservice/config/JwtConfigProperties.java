package com.pgoptima.userservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtConfigProperties {
    private String secret;
    private long accessTokenExpirationMs = 3600000;   // 1 час
    private long refreshTokenExpirationMs = 86400000; // 24 часа
    private String issuer = "pgoptima-auth-service";
}