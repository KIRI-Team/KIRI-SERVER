package com.kiri.server.domain.taste.caculator;

import com.kiri.server.domain.taste.dto.TasteSourceDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FamilyTasteScoreCalculatorTest {

    private final FamilyTasteScoreCalculator calculator = new FamilyTasteScoreCalculator();

    @Test
    @DisplayName("유효한 구성원 점수들의 평균을 계산한다")
    void calculateAverageScore() {
        List<TasteSourceDTO.MemberScore> memberScores = List.of(createMemberScore("0.8"), createMemberScore("0.6"), createMemberScore("0.7"));

        BigDecimal result = calculator.calculate(memberScores);

        assertThat(result).isEqualByComparingTo("0.7");
    }

    @Test
    @DisplayName("유효한 구성원이 한 명이면 해당 구성원의 점수를 반환한다")
    void calculateSingleMemberScore() {
        List<TasteSourceDTO.MemberScore> memberScores = List.of(createMemberScore("0.8"));

        BigDecimal result = calculator.calculate(memberScores);

        assertThat(result).isEqualByComparingTo("0.8");
    }

    @Test
    @DisplayName("유효한 구성원이 없으면 0점을 반환한다")
    void calculateEmptyMemberScores() {
        List<TasteSourceDTO.MemberScore> memberScores = List.of();

        BigDecimal result = calculator.calculate(memberScores);

        assertThat(result).isEqualByComparingTo("0");
    }

    private TasteSourceDTO.MemberScore createMemberScore(String total) {
        return TasteSourceDTO.MemberScore.builder()
                .preferenceActivity(BigDecimal.ZERO)
                .space(BigDecimal.ZERO)
                .price(BigDecimal.ZERO)
                .duration(BigDecimal.ZERO)
                .activityLevel(BigDecimal.ZERO)
                .total(new BigDecimal(total))
                .build();
    }

}