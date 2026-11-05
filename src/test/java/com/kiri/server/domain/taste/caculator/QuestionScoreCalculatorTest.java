package com.kiri.server.domain.taste.caculator;

import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.question.entity.MemberAnswer;
import com.kiri.server.domain.question.entity.MemberAnswerCategory;
import com.kiri.server.domain.taste.enums.ActivityLevel;
import com.kiri.server.domain.taste.enums.DurationLevel;
import com.kiri.server.domain.taste.enums.PriceLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionScoreCalculatorTest {

    private final QuestionScoreCalculator calculator = new QuestionScoreCalculator();

    @Test
    @DisplayName("오늘의 질문 답변은 가중치가 1이다.")
    void calculateAnswerWeightToday() {
        BigDecimal result = calculator.calculateAnswerWeight(0);

        assertThat(result).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("30일 전 질문 답변은 가중치가 0.5이다.")
    void calculateAnswerWeight30DaysAgo() {
        BigDecimal result = calculator.calculateAnswerWeight(30);

        assertThat(result).isEqualByComparingTo("0.5");
    }

    @Test
    @DisplayName("60일 전 질문 답변은 가중치가 0.25이다.")
    void calculateAnswerWeight60DaysAgo() {
        BigDecimal result = calculator.calculateAnswerWeight(60);

        assertThat(result).isEqualByComparingTo("0.25");
    }

    @Test
    @DisplayName("지난 일수가 음수이면 예외가 발생한다.")
    void calculateAnswerWeightNegativeDays() {
        assertThatThrownBy(() -> calculator.calculateAnswerWeight(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("질문 무게 합이 2이면 신뢰도 c는 1이다.")
    void calculateReliabilityMax() {
        BigDecimal result = calculator.calculateReliability(new BigDecimal("2"));

        assertThat(result).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("질문 무게 합이 1이면 신뢰도 c는 0.5이다.")
    void calculateReliabilityHalf() {
        BigDecimal result = calculator.calculateReliability(new BigDecimal("1"));

        assertThat(result).isEqualByComparingTo("0.5");
    }

    @Test
    @DisplayName("질문 무게 합이 2를 초과하면 신뢰도 c는 1로 제한된다.")
    void calculateReliabilityOverMax() {
        BigDecimal result = calculator.calculateReliability(new BigDecimal("3"));

        assertThat(result).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("질문 무게 합이 0이면 신뢰도 c는 0이다.")
    void calculateReliabilityZero() {
        BigDecimal result = calculator.calculateReliability(BigDecimal.ZERO);

        assertThat(result).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("여러 질문 답변의 시간 감쇠 가중치를 합산한다.")
    void calculateQuestionWeightSum() {
        LocalDate today = LocalDate.of(2026, 11, 3);

        MemberAnswer todayAnswer = mock(MemberAnswer.class);
        MemberAnswer thirtyDaysAgoAnswer = mock(MemberAnswer.class);

        when(todayAnswer.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 11, 3, 10, 0));
        when(thirtyDaysAgoAnswer.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 10, 4, 10, 0));

        BigDecimal result = calculator.calculateQuestionWeightSum(List.of(todayAnswer, thirtyDaysAgoAnswer), today);

        assertThat(result).isEqualByComparingTo("1.5");
    }

    @Test
    @DisplayName("질문 답변의 가중치 합으로 신뢰도 c를 계산한다.")
    void calculateReliabilityFromAnswers() {
        LocalDate today = LocalDate.of(2026, 11, 3);

        MemberAnswer todayAnswer = mock(MemberAnswer.class);
        MemberAnswer thirtyDaysAgoAnswer = mock(MemberAnswer.class);

        when(todayAnswer.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 11, 3, 10, 0));

        when(thirtyDaysAgoAnswer.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 10, 4, 10, 0));

        BigDecimal result = calculator.calculateReliability(List.of(todayAnswer, thirtyDaysAgoAnswer), today);

        assertThat(result).isEqualByComparingTo("0.75");
    }

    @Test
    @DisplayName("공간 값의 질문 점수는 해당 값의 질문 무게를 전체 질문 무게로 나누어 계산한다.")
    void calculateIndoorOutdoorScore() {
        LocalDate today = LocalDate.of(2026, 11, 3);

        MemberAnswer indoorAnswer = mock(MemberAnswer.class);
        MemberAnswer outdoorAnswer = mock(MemberAnswer.class);

        when(indoorAnswer.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 11, 3, 10, 0));

        when(indoorAnswer.getIndoorOutdoor()).thenReturn(IndoorOutdoor.INDOOR);

        when(outdoorAnswer.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 10, 4, 10, 0));

        when(outdoorAnswer.getIndoorOutdoor()).thenReturn(IndoorOutdoor.OUTDOOR);

        BigDecimal result = calculator.calculateIndoorOutdoorScore(List.of(indoorAnswer, outdoorAnswer), today, IndoorOutdoor.INDOOR);

        assertThat(result).isEqualByComparingTo("0.6666666667");
    }

    @Test
    @DisplayName("비용 값의 질문 점수를 계산한다.")
    void calculatePriceScore() {
        LocalDate today = LocalDate.of(2026, 11, 3);

        MemberAnswer freeAnswer = mock(MemberAnswer.class);
        MemberAnswer lowAnswer = mock(MemberAnswer.class);

        when(freeAnswer.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 11, 3, 10, 0));
        when(freeAnswer.getPriceLevel()).thenReturn(PriceLevel.FREE);

        when(lowAnswer.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 10, 4, 10, 0));
        when(lowAnswer.getPriceLevel()).thenReturn(PriceLevel.LOW);

        BigDecimal result = calculator.calculatePriceScore(List.of(freeAnswer, lowAnswer), today, PriceLevel.FREE);

        assertThat(result).isEqualByComparingTo("0.6666666667");
    }

    @Test
    @DisplayName("활동시간 값의 질문 점수를 계산한다.")
    void calculateDurationScore() {
        LocalDate today = LocalDate.of(2026, 11, 3);

        MemberAnswer shortAnswer = mock(MemberAnswer.class);
        MemberAnswer longAnswer = mock(MemberAnswer.class);

        when(shortAnswer.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 11, 3, 10, 0));
        when(shortAnswer.getDurationLevel()).thenReturn(DurationLevel.WITHIN_1H);

        when(longAnswer.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 10, 4, 10, 0));
        when(longAnswer.getDurationLevel()).thenReturn(DurationLevel.H1_TO_3);

        BigDecimal result = calculator.calculateDurationScore(List.of(shortAnswer, longAnswer), today, DurationLevel.WITHIN_1H);

        assertThat(result).isEqualByComparingTo("0.6666666667");
    }

    @Test
    @DisplayName("활동량 값의 질문 점수를 계산한다.")
    void calculateActivityLevelScore() {
        LocalDate today = LocalDate.of(2026, 11, 3);

        MemberAnswer lightAnswer = mock(MemberAnswer.class);
        MemberAnswer activeAnswer = mock(MemberAnswer.class);

        when(lightAnswer.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 11, 3, 10, 0));
        when(lightAnswer.getActivityLevel()).thenReturn(ActivityLevel.LIGHT);

        when(activeAnswer.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 10, 4, 10, 0));
        when(activeAnswer.getActivityLevel()).thenReturn(ActivityLevel.ACTIVE);

        BigDecimal result = calculator.calculateActivityLevelScore(List.of(lightAnswer, activeAnswer), today, ActivityLevel.LIGHT);

        assertThat(result).isEqualByComparingTo("0.6666666667");
    }

    @Test
    @DisplayName("카테고리별 질문 점수는 무게 합을 1로 제한하고 상위 3개만 반환한다.")
    void calculateCategoryScores() {
        LocalDate today = LocalDate.of(2026, 11, 3);

        MemberAnswer firstAnswer = mock(MemberAnswer.class);
        MemberAnswer secondAnswer = mock(MemberAnswer.class);
        MemberAnswer thirdAnswer = mock(MemberAnswer.class);
        MemberAnswer fourthAnswer = mock(MemberAnswer.class);

        when(firstAnswer.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 11, 3, 10, 0));
        when(secondAnswer.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 10, 4, 10, 0));
        when(thirdAnswer.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 10, 4, 10, 0));
        when(fourthAnswer.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 10, 4, 10, 0));

        when(firstAnswer.getId()).thenReturn(1L);
        when(secondAnswer.getId()).thenReturn(2L);
        when(thirdAnswer.getId()).thenReturn(3L);
        when(fourthAnswer.getId()).thenReturn(4L);

        MemberAnswerCategory firstCategory = mock(MemberAnswerCategory.class);
        MemberAnswerCategory secondCategory = mock(MemberAnswerCategory.class);
        MemberAnswerCategory thirdCategory = mock(MemberAnswerCategory.class);
        MemberAnswerCategory fourthCategory = mock(MemberAnswerCategory.class);

        when(firstCategory.getMemberAnswer()).thenReturn(firstAnswer);
        when(secondCategory.getMemberAnswer()).thenReturn(secondAnswer);
        when(thirdCategory.getMemberAnswer()).thenReturn(thirdAnswer);
        when(fourthCategory.getMemberAnswer()).thenReturn(fourthAnswer);

        when(firstCategory.getCategory()).thenReturn(Category.EXHIBITION);
        when(secondCategory.getCategory()).thenReturn(Category.EXHIBITION);
        when(thirdCategory.getCategory()).thenReturn(Category.FOOD_CAFE);
        when(fourthCategory.getCategory()).thenReturn(Category.OUTDOOR);

        Map<Category, BigDecimal> result = calculator.calculateCategoryScores(List.of(firstAnswer, secondAnswer, thirdAnswer, fourthAnswer), List.of(firstCategory, secondCategory, thirdCategory, fourthCategory), today);

        assertThat(result.get(Category.EXHIBITION)).isEqualByComparingTo(BigDecimal.ONE);

        assertThat(result.get(Category.FOOD_CAFE)).isEqualByComparingTo(new BigDecimal("0.5"));

        assertThat(result.get(Category.OUTDOOR)).isEqualByComparingTo(new BigDecimal("0.5"));

        assertThat(result).hasSize(3);
    }
}