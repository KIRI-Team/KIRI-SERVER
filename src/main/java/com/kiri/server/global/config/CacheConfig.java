package com.kiri.server.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String KMA_FORECAST = "kmaForecast";

    // 당일 고정용 02시 발표분이 하루 동안 남아 있도록 25시간
    private static final Duration KMA_FORECAST_TTL = Duration.ofHours(25);
    private static final long KMA_FORECAST_MAX_SIZE = 500;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(KMA_FORECAST);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(KMA_FORECAST_TTL)
                .maximumSize(KMA_FORECAST_MAX_SIZE)
                .recordStats());
        return cacheManager;
    }
}
