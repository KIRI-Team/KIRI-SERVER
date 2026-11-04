package com.kiri.server.domain.taste.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DurationLevel {
    WITHIN_1H(0, 60),
    H1_TO_3(60, 180),
    HALF_DAY_PLUS(180, null);

    private final Integer minMinutes;
    private final Integer maxMinutes;
}
