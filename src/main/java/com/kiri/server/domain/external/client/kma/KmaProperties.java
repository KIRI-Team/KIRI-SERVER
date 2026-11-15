package com.kiri.server.domain.external.client.kma;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "kma")
public record KmaProperties(
        String baseUrl,
        String authKey,
        Duration connectTimeout,
        Duration readTimeout
) {

    public boolean hasAuthKey() {
        return authKey != null && !authKey.isBlank();
    }
}
