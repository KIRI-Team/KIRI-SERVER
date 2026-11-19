package com.kiri.server.domain.taste.caculator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MissionScoreCalculatorTest {

    private final MissionScoreCalculator calculator = new MissionScoreCalculator();

    @Test
    @DisplayName("가족 취향 점수와 일반 점수를 가중 합산한다")
    void calculateFinalScore() {
        BigDecimal familyTasteScore = new BigDecimal("0.8");
        BigDecimal generalScore = new BigDecimal("0.7");

        BigDecimal result = calculator.calculate(familyTasteScore, generalScore);

        assertThat(result).isEqualByComparingTo("0.78");
    }

    @Test
    @DisplayName("가족 취향 점수가 0점이면 일반 점수의 20퍼센트만 반영한다")
    void calculateWithoutFamilyTasteScore() {
        BigDecimal familyTasteScore = BigDecimal.ZERO;
        BigDecimal generalScore = BigDecimal.ONE;

        BigDecimal result = calculator.calculate(familyTasteScore, generalScore);

        assertThat(result).isEqualByComparingTo("0.2");
    }

    @Test
    @DisplayName("일반 점수가 0점이면 가족 취향 점수의 80퍼센트만 반영한다")
    void calculateWithoutGeneralScore() {
        BigDecimal familyTasteScore = BigDecimal.ONE;
        BigDecimal generalScore = BigDecimal.ZERO;

        BigDecimal result = calculator.calculate(familyTasteScore, generalScore);

        assertThat(result).isEqualByComparingTo("0.8");
    }

    @Test
    @DisplayName("두 점수가 모두 1점이면 최종 점수는 1점이다")
    void calculateMaximumScore() {
        BigDecimal familyTasteScore = BigDecimal.ONE;
        BigDecimal generalScore = BigDecimal.ONE;

        BigDecimal result = calculator.calculate(familyTasteScore, generalScore);

        assertThat(result).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("가족 취향 점수가 null이면 예외가 발생한다")
    void calculateWithNullFamilyTasteScore() {
        assertThatThrownBy(() -> calculator.calculate(null, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("일반 점수가 null이면 예외가 발생한다")
    void calculateWithNullGeneralScore() {
        assertThatThrownBy(() -> calculator.calculate(BigDecimal.ONE, null)).isInstanceOf(IllegalArgumentException.class);
    }
}