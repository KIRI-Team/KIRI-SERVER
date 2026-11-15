package com.kiri.server.domain.weather.service;

import com.kiri.server.domain.external.client.kma.KmaGrid;
import com.kiri.server.domain.external.client.kma.dto.KmaResDTO;
import com.kiri.server.domain.external.exception.ExternalException;
import com.kiri.server.domain.external.exception.code.ExternalErrorCode;
import com.kiri.server.domain.weather.dto.WeatherSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

    private static final BigDecimal LATITUDE = new BigDecimal("37.5796");
    private static final BigDecimal LONGITUDE = new BigDecimal("126.9770");
    private static final KmaGrid GRID = KmaGrid.from(37.5796, 126.9770);
    // 지금 2026-11-15 14:20 → 최신 발표 14:00, 당일 첫 발표 02:00
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T05:20:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final LocalDateTime LATEST_BASE = TODAY.atTime(14, 0);
    private static final LocalDateTime TODAY_FIRST_BASE = TODAY.atTime(2, 0);

    @Mock
    private ForecastLoader forecastLoader;

    private WeatherService weatherService;

    @BeforeEach
    void setUp() {
        weatherService = new WeatherService(forecastLoader, CLOCK);
    }

    @Test
    @DisplayName("미래 일정은 최신 발표분에서 구간 안 예보만 요약한다.")
    void summarize_futureSchedule() {
        LocalDateTime start = TODAY.plusDays(1).atTime(14, 0);
        given(forecastLoader.load(GRID, LATEST_BASE)).willReturn(List.of(
                item("TMP", start.minusHours(1), "30"),
                item("TMP", start, "10"),
                item("TMP", start.plusHours(1), "16"),
                item("TMP", start.plusHours(2), "12"),
                item("TMP", start.plusHours(4), "-5"),
                item("POP", start, "20"),
                item("POP", start.plusHours(2), "70"),
                item("PTY", start, "0"),
                item("PTY", start.plusHours(1), "1")));

        assertThat(weatherService.summarize(LATITUDE, LONGITUDE, start, start.plusHours(3)))
                .contains(new WeatherSummary(16, 10, 70, true));
    }

    @Test
    @DisplayName("정시에 걸치지 않는 일정은 앞뒤 정시 예보까지 넓혀 본다.")
    void summarize_widensToWholeHours() {
        LocalDateTime start = TODAY.plusDays(1).atTime(14, 30);
        given(forecastLoader.load(GRID, LATEST_BASE)).willReturn(List.of(
                item("TMP", start.minusMinutes(30), "15"),
                item("TMP", start.plusMinutes(30), "13"),
                item("PTY", start.plusMinutes(90), "1"),
                item("PTY", start.plusMinutes(150), "3")));

        assertThat(weatherService.summarize(LATITUDE, LONGITUDE, start, start.plusHours(1)))
                .contains(new WeatherSummary(15, 13, 0, true));
    }

    @Test
    @DisplayName("당일 일정은 그날 02시 발표분으로 조회한다.")
    void summarize_todaySchedule() {
        LocalDateTime start = TODAY.atTime(18, 0);
        given(forecastLoader.load(GRID, TODAY_FIRST_BASE)).willReturn(List.of(
                item("TMP", start, "8"),
                item("POP", start, "0"),
                item("PTY", start, "0")));

        assertThat(weatherService.summarize(LATITUDE, LONGITUDE, start, start.plusHours(3)))
                .contains(new WeatherSummary(8, 8, 0, false));
    }

    @Test
    @DisplayName("당일 02시 발표 전이면 최신 발표분을 쓴다.")
    void baseTime_beforeTodayFirst() {
        LocalDateTime now = TODAY.atTime(1, 30);

        assertThat(WeatherService.baseTime(TODAY, now)).isEqualTo(TODAY.minusDays(1).atTime(23, 0));
    }

    @Test
    @DisplayName("예보 범위 밖이면 조회하지 않고 날씨 없음이다.")
    void summarize_beyondForecastRange() {
        LocalDateTime start = TODAY.plusDays(WeatherService.FORECAST_DAYS).atStartOfDay();

        assertThat(weatherService.summarize(LATITUDE, LONGITUDE, start, start.plusHours(3))).isEmpty();
        verifyNoInteractions(forecastLoader);
    }

    @Test
    @DisplayName("이미 지난 구간이면 조회하지 않고 날씨 없음이다.")
    void summarize_pastSchedule() {
        LocalDateTime start = TODAY.minusDays(1).atTime(14, 0);

        assertThat(weatherService.summarize(LATITUDE, LONGITUDE, start, start.plusHours(3))).isEmpty();
        verifyNoInteractions(forecastLoader);
    }

    @Test
    @DisplayName("예보 조회에 실패하면 날씨 없음이다.")
    void summarize_loadFailed() {
        LocalDateTime start = TODAY.plusDays(1).atTime(14, 0);
        given(forecastLoader.load(any(), any()))
                .willThrow(new ExternalException(ExternalErrorCode.CALL_FAILED));

        assertThat(weatherService.summarize(LATITUDE, LONGITUDE, start, start.plusHours(3))).isEmpty();
    }

    @Test
    @DisplayName("구간 안 기온이 결측값뿐이면 날씨 없음이다.")
    void summarize_missingTemperature() {
        LocalDateTime start = TODAY.plusDays(1).atTime(14, 0);
        given(forecastLoader.load(GRID, LATEST_BASE)).willReturn(List.of(
                item("TMP", start, "-999"),
                item("POP", start, "80")));

        assertThat(weatherService.summarize(LATITUDE, LONGITUDE, start, start.plusHours(3))).isEmpty();
    }

    private static KmaResDTO.ForecastItem item(String category, LocalDateTime forecastAt, String value) {
        return new KmaResDTO.ForecastItem(category, forecastAt, value);
    }
}
