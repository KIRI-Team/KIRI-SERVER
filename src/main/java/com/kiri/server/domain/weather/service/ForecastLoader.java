package com.kiri.server.domain.weather.service;

import com.kiri.server.domain.external.client.kma.KmaForecastClient;
import com.kiri.server.domain.external.client.kma.KmaGrid;
import com.kiri.server.domain.external.client.kma.dto.KmaResDTO;
import com.kiri.server.global.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

// 프록시를 거쳐야 캐시가 적용되므로 WeatherService와 다른 빈
@Component
@RequiredArgsConstructor
public class ForecastLoader {

    static final String TEMPERATURE = "TMP";
    static final String PRECIPITATION_PROBABILITY = "POP";
    static final String PRECIPITATION_TYPE = "PTY";
    private static final Set<String> USED_CATEGORIES = Set.of(TEMPERATURE, PRECIPITATION_PROBABILITY, PRECIPITATION_TYPE);

    private final KmaForecastClient kmaForecastClient;

    // 격자＋발표시각이 키. 같은 키 동시 요청은 한 번만 호출(sync), 실패는 캐시하지 않음
    @Cacheable(cacheNames = CacheConfig.KMA_FORECAST, sync = true)
    public List<KmaResDTO.ForecastItem> load(KmaGrid grid, LocalDateTime baseTime) {
        return kmaForecastClient.fetchVillageForecast(grid, baseTime).stream()
                .filter(item -> USED_CATEGORIES.contains(item.category()))
                .toList();
    }
}
