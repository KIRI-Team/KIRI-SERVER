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
        Duration readTimeout,
        Integer maxCallsPerRun,
        Integer dailyCallLimit
) {

    private static final int DEFAULT_NUM_OF_ROWS = 1000;
    private static final int DEFAULT_MAX_CALLS_PER_RUN = 240;
    private static final int DEFAULT_DAILY_CALL_LIMIT = 900;

    public TourApiProperties {
        numOfRows = numOfRows == null ? DEFAULT_NUM_OF_ROWS : numOfRows;
        maxCallsPerRun = maxCallsPerRun == null ? DEFAULT_MAX_CALLS_PER_RUN : maxCallsPerRun;
        dailyCallLimit = dailyCallLimit == null ? DEFAULT_DAILY_CALL_LIMIT : dailyCallLimit;
    }

    public boolean hasServiceKey() {
        return serviceKey != null && !serviceKey.isBlank();
    }
}
