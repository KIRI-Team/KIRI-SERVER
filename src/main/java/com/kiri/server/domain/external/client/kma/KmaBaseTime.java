package com.kiri.server.domain.external.client.kma;

import java.time.LocalDateTime;
import java.time.LocalTime;

// 단기예보 발표시각 (02시부터 3시간 간격, 발표 10분 뒤부터 조회 가능)
public final class KmaBaseTime {

    private static final int FIRST_HOUR = 2;
    private static final int INTERVAL_HOURS = 3;
    private static final int AVAILABLE_AFTER_MINUTES = 10;

    private KmaBaseTime() {
    }

    public static LocalDateTime latest(LocalDateTime now) {
        LocalDateTime available = now.minusMinutes(AVAILABLE_AFTER_MINUTES);
        int hour = available.getHour();
        if (hour < FIRST_HOUR) {
            return available.toLocalDate().minusDays(1).atTime(LocalTime.of(23, 0));
        }
        int baseHour = hour - (hour - FIRST_HOUR) % INTERVAL_HOURS;
        return available.toLocalDate().atTime(baseHour, 0);
    }
}
