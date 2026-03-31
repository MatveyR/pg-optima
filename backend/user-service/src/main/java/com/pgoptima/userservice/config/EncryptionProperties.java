package com.pgoptima.userservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "encryption")
public class EncryptionProperties {
    private String secretKey; // 32-байтовый ключ для AES (base64 или hex)
    private String salt = "PgOptimaSalt";
}