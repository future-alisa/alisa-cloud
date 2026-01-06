package com.alisa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private String secret = "dGhpcy1pcy1hLWRlZmF1bHQtc2VjcmV0LWtleS1mb3ItZGV2ZWxvcG1lbnQtcHVycG9zZXM=";
}
