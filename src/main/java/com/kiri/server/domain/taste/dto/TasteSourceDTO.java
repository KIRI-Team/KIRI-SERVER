package com.kiri.server.domain.taste.dto;

import lombok.Builder;

import java.math.BigDecimal;

public class TasteSourceDTO {

    @Builder
    public record MemberScore(
            BigDecimal preferenceActivity,
            BigDecimal space,
            BigDecimal price,
            BigDecimal duration,
            BigDecimal activityLevel,
            BigDecimal total
    ) {}

    @Builder
    public record PreferenceWeight(
            BigDecimal questionWeight,
            BigDecimal tasteTestWeight
    ) {}
}
