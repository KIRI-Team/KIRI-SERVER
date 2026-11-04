package com.kiri.server.domain.taste.caculator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PreferenceWeightCalculatorTest {

    private final PreferenceWeightCalculator calculator = new PreferenceWeightCalculator();

    @Test
    @DisplayName("질문과 취향 테스트가 모두 있으면 취향 테스트 무게가 더 크다.")
    void calculate_both() {
        BigDecimal c = new BigDecimal("1.0");

        var result = calculator.calculate(c, true, true);

        assertThat(result.questionWeight()).isEqualByComparingTo("0.35");

        assertThat(result.tasteTestWeight()).isEqualByComparingTo("0.45");
    }

    @Test
    @DisplayName("질문만 있으면 질문 무게는 c이다.")
    void calculate_questionOnly() {
        var result = calculator.calculate(new BigDecimal("0.7"), true, false);

        assertThat(result.questionWeight()).isEqualByComparingTo("0.7");

        assertThat(result.tasteTestWeight()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("취향 테스트만 있으면 취향 테스트 무게는 0.8이다.")
    void calculate_tasteTestOnly() {
        var result = calculator.calculate(new BigDecimal("0.7"), false, true);

        assertThat(result.questionWeight()).isEqualByComparingTo("0");

        assertThat(result.tasteTestWeight()).isEqualByComparingTo("0.8");
    }

    @Test
    @DisplayName("질문 무게가 최대여도 취향 테스트 무게가 더 크다.")
    void calculate_both_maxQuestionReliability() {
        var result = calculator.calculate(new BigDecimal("1.0"), true, true);

        assertThat(result.tasteTestWeight()).isGreaterThan(result.questionWeight());
    }
}