package com.pgoptima.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtConfigProperties {
    private String secret;
    private String issuer = "pgoptima-auth-service";
}