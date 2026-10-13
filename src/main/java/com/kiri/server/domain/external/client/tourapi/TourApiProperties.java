package com.kiri.server.domain.external.client.tourapi;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "tour-api")
public record TourApiProperties(
        String baseUrl,
        String serviceKey,
        String mobileApp,
        Integer numOfRows,
        String regionCode,
        Duration connectTimeout,
        Duration readTimeout
) {

    public boolean hasServiceKey() {
        return serviceKey != null && !serviceKey.isBlank();
    }
}
