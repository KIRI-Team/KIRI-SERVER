package com.kiri.server.domain.weather.service;

import com.kiri.server.domain.external.client.kma.KmaForecastClient;
import com.kiri.server.domain.external.client.kma.KmaGrid;
import com.kiri.server.domain.external.client.kma.dto.KmaResDTO;
import com.kiri.server.domain.external.exception.ExternalException;
import com.kiri.server.domain.external.exception.code.ExternalErrorCode;
import com.kiri.server.global.config.CacheConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 캐시는 스프링 프록시로 동작하므로 캐시 설정과 함께 띄워 확인
@SpringJUnitConfig({CacheConfig.class, ForecastLoader.class})
class ForecastLoaderTest {

    private static final KmaGrid GRID = new KmaGrid(60, 127);
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 11, 15, 14, 0);
    private static final LocalDateTime FORECAST_AT = BASE_TIME.plusHours(1);

    @Autowired
    private ForecastLoader forecastLoader;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private KmaForecastClient kmaForecastClient;

    @BeforeEach
    void clearCache() {
        cacheManager.getCache(CacheConfig.KMA_FORECAST).clear();
    }

    @Test
    @DisplayName("같은 격자·발표시각은 한 번만 호출하고, 발표시각이 바뀌면 다시 호출한다.")
    void load_cachedByGridAndBaseTime() {
        LocalDateTime nextBaseTime = BASE_TIME.plusHours(3);
        given(kmaForecastClient.fetchVillageForecast(GRID, BASE_TIME)).willReturn(List.of(item("TMP")));
        given(kmaForecastClient.fetchVillageForecast(GRID, nextBaseTime)).willReturn(List.of(item("TMP")));

        forecastLoader.load(GRID, BASE_TIME);
        forecastLoader.load(GRID, BASE_TIME);
        forecastLoader.load(GRID, nextBaseTime);

        verify(kmaForecastClient, times(1)).fetchVillageForecast(GRID, BASE_TIME);
        verify(kmaForecastClient, times(1)).fetchVillageForecast(GRID, nextBaseTime);
    }

    @Test
    @DisplayName("조회 실패는 캐시하지 않아 다음 요청에서 다시 호출한다.")
    void load_failureNotCached() {
        given(kmaForecastClient.fetchVillageForecast(GRID, BASE_TIME))
                .willThrow(new ExternalException(ExternalErrorCode.CALL_FAILED))
                .willReturn(List.of(item("TMP")));

        assertThatThrownBy(() -> forecastLoader.load(GRID, BASE_TIME))
                .isInstanceOf(ExternalException.class);
        assertThat(forecastLoader.load(GRID, BASE_TIME)).hasSize(1);
        verify(kmaForecastClient, times(2)).fetchVillageForecast(GRID, BASE_TIME);
    }

    @Test
    @DisplayName("예보가 비어 있으면 캐시하지 않고 예외로 넘겨 다음 요청에서 다시 호출한다.")
    void load_emptyNotCached() {
        given(kmaForecastClient.fetchVillageForecast(GRID, BASE_TIME))
                .willReturn(List.of())
                .willReturn(List.of(item("TMP")));

        assertThatThrownBy(() -> forecastLoader.load(GRID, BASE_TIME))
                .isInstanceOf(ExternalException.class);
        assertThat(forecastLoader.load(GRID, BASE_TIME)).hasSize(1);
        verify(kmaForecastClient, times(2)).fetchVillageForecast(GRID, BASE_TIME);
    }

    @Test
    @DisplayName("기온·강수확률·강수형태만 남긴다.")
    void load_keepsUsedCategories() {
        given(kmaForecastClient.fetchVillageForecast(GRID, BASE_TIME)).willReturn(List.of(
                item("TMP"), item("POP"), item("PTY"), item("SKY"), item("WSD"), item("REH")));

        assertThat(forecastLoader.load(GRID, BASE_TIME))
                .extracting(KmaResDTO.ForecastItem::category)
                .containsExactly("TMP", "POP", "PTY");
    }

    private static KmaResDTO.ForecastItem item(String category) {
        return new KmaResDTO.ForecastItem(category, FORECAST_AT, "1");
    }
}
