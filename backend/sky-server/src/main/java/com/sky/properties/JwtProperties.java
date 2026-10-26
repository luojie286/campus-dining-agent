package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "sky.jwt")
public class JwtProperties {

    private String secretKey;

    private Duration ttl = Duration.ofHours(2);
}