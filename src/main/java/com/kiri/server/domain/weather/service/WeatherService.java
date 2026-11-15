package com.kiri.server.domain.weather.service;

import com.kiri.server.domain.external.client.kma.KmaBaseTime;
import com.kiri.server.domain.external.client.kma.KmaGrid;
import com.kiri.server.domain.external.client.kma.dto.KmaResDTO;
import com.kiri.server.domain.external.exception.ExternalException;
import com.kiri.server.domain.weather.dto.WeatherSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.stream.DoubleStream;

// 예보 범위 밖이거나 조회에 실패하면 날씨 없음
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    static final int FORECAST_DAYS = 5;
    private static final String NO_PRECIPITATION = "0";

    private final ForecastLoader forecastLoader;
    private final Clock clock;

    // 일정 구간 요약
    public Optional<WeatherSummary> summarize(BigDecimal latitude, BigDecimal longitude,
                                              LocalDateTime start, LocalDateTime end) {
        if (latitude == null || longitude == null || start == null || !start.isBefore(end)) {
            return Optional.empty();
        }
        LocalDateTime baseTime = baseTime(start.toLocalDate(), LocalDateTime.now(clock));
        LocalDateTime forecastEnd = baseTime.toLocalDate().plusDays(FORECAST_DAYS).atStartOfDay();
        if (!end.isAfter(baseTime) || !start.isBefore(forecastEnd)) {
            return Optional.empty();
        }

        KmaGrid grid = KmaGrid.from(latitude.doubleValue(), longitude.doubleValue());
        List<KmaResDTO.ForecastItem> forecasts;
        try {
            forecasts = forecastLoader.load(grid, baseTime);
        } catch (ExternalException e) {
            log.warn("[Weather] 단기예보 조회 실패: grid={}, baseTime={}, reason={}", grid, baseTime, e.getMessage());
            return Optional.empty();
        }

        // 예보가 정시 값뿐이라 일정이 걸친 시간대를 앞뒤 정시까지 넓혀 봄
        LocalDateTime from = start.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime to = ceilToHour(end);
        List<KmaResDTO.ForecastItem> inRange = forecasts.stream()
                .filter(item -> !item.forecastAt().isBefore(from) && !item.forecastAt().isAfter(to))
                .toList();
        return summarize(inRange);
    }

    private static LocalDateTime ceilToHour(LocalDateTime time) {
        LocalDateTime hour = time.truncatedTo(ChronoUnit.HOURS);
        return hour.isEqual(time) ? hour : hour.plusHours(1);
    }

    // 당일 일정은 그날 02시 발표분으로 고정해 조회할 때마다 맞춤템이 바뀌지 않게 함
    static LocalDateTime baseTime(LocalDate scheduleDate, LocalDateTime now) {
        LocalDateTime latest = KmaBaseTime.latest(now);
        LocalDateTime todayFirst = KmaBaseTime.firstOf(now.toLocalDate());
        if (scheduleDate.isEqual(now.toLocalDate()) && !latest.isBefore(todayFirst)) {
            return todayFirst;
        }
        return latest;
    }

    private static Optional<WeatherSummary> summarize(List<KmaResDTO.ForecastItem> items) {
        OptionalDouble max = numbers(items, ForecastLoader.TEMPERATURE).max();
        OptionalDouble min = numbers(items, ForecastLoader.TEMPERATURE).min();
        if (max.isEmpty()) {
            return Optional.empty();
        }
        OptionalInt maxProbability = numbers(items, ForecastLoader.PRECIPITATION_PROBABILITY).mapToInt(value -> (int) value).max();
        boolean precipitation = items.stream()
                .anyMatch(item -> ForecastLoader.PRECIPITATION_TYPE.equals(item.category())
                        && item.value() != null && !NO_PRECIPITATION.equals(item.value()));
        return Optional.of(new WeatherSummary(
                max.getAsDouble(), min.getAsDouble(), maxProbability.orElse(0), precipitation));
    }

    private static DoubleStream numbers(List<KmaResDTO.ForecastItem> items, String category) {
        return items.stream()
                .filter(item -> category.equals(item.category()))
                .map(KmaResDTO.ForecastItem::value)
                .flatMap(value -> parse(value).stream())
                .mapToDouble(Double::doubleValue);
    }

    // 결측값(-999 등)이나 숫자가 아닌 값은 건너뜀
    private static Optional<Double> parse(String value) {
        try {
            double number = Double.parseDouble(value);
            return number <= -900 ? Optional.empty() : Optional.of(number);
        } catch (NumberFormatException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
