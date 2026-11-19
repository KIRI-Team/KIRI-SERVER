package com.kiri.server.domain.external.client.kma;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class KmaBaseTimeTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "2026-11-15T00:30, 2026-11-14T23:00",
            "2026-11-15T02:09, 2026-11-14T23:00",
            "2026-11-15T02:10, 2026-11-15T02:00",
            "2026-11-15T05:09, 2026-11-15T02:00",
            "2026-11-15T05:10, 2026-11-15T05:00",
            "2026-11-15T23:59, 2026-11-15T23:00"
    })
    @DisplayName("발표 10분 뒤부터 그 발표시각을 쓰고, 02시 발표 전이면 전날 23시를 쓴다.")
    void latest(LocalDateTime now, LocalDateTime expected) {
        assertThat(KmaBaseTime.latest(now)).isEqualTo(expected);
    }
}
