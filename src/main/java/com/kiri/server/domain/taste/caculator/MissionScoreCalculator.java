package com.kiri.server.domain.taste.caculator;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class MissionScoreCalculator {

    private static final BigDecimal FAMILY_TASTE_WEIGHT = new BigDecimal("0.8");
    private static final BigDecimal GENERAL_WEIGHT = new BigDecimal("0.2");

    private static final int SCALE = 10;

    public BigDecimal calculate(BigDecimal familyTasteScore, BigDecimal generalScore) {
        if (familyTasteScore == null || generalScore == null) {
            throw new IllegalArgumentException("가족 취향 점수와 일반 점수는 null일 수 없습니다.");
        }

        return familyTasteScore.multiply(FAMILY_TASTE_WEIGHT).add(generalScore.multiply(GENERAL_WEIGHT)).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
