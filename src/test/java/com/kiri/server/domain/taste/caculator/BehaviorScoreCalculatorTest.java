package com.kiri.server.domain.taste.caculator;

import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.review.entity.Review;
import com.kiri.server.domain.schedule.entity.Schedule;
import org.junit.jupiter.api.BeforeEach;
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

class BehaviorScoreCalculatorTest {

    private BehaviorScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new BehaviorScoreCalculator();
    }

    @Test
    @DisplayName("오늘 발생한 행동의 감쇠 가중치는 1이다.")
    void calculateBehaviorWeightToday() {
        BigDecimal result = calculator.calculateBehaviorWeight(0);

        assertThat(result).isEqualByComparingTo("1.0");
    }

    @Test
    @DisplayName("90일 전에 발생한 행동의 감쇠 가중치는 0.5이다.")
    void calculateBehaviorWeight90DaysAgo() {
        BigDecimal result = calculator.calculateBehaviorWeight(90);

        assertThat(result).isEqualByComparingTo("0.5");
    }

    @Test
    @DisplayName("180일 전에 발생한 행동의 감쇠 가중치는 0.25이다.")
    void calculateBehaviorWeight180DaysAgo() {
        BigDecimal result = calculator.calculateBehaviorWeight(180);

        assertThat(result).isEqualByComparingTo("0.25");
    }

    @Test
    @DisplayName("지난 일수가 음수이면 예외가 발생한다.")
    void calculateBehaviorWeightNegativeDaysThrowsException() {
        assertThatThrownBy(() -> calculator.calculateBehaviorWeight(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("버킷을 1회 저장하면 0.3점이다.")
    void calculateBucketScoreOneSave() {
        BigDecimal result = calculator.calculateBucketScore(1);

        assertThat(result).isEqualByComparingTo("0.3");
    }

    @Test
    @DisplayName("버킷을 2회 저장하면 0.6점이다.")
    void calculateBucketScoreTwoSaves() {
        BigDecimal result = calculator.calculateBucketScore(2);

        assertThat(result).isEqualByComparingTo("0.6");
    }

    @Test
    @DisplayName("버킷 저장 횟수가 2회를 초과해도 최대 0.6점까지만 반영된다.")
    void calculateBucketScoreMoreThanTwoSavesIsLimitedToTwo() {
        BigDecimal result = calculator.calculateBucketScore(5);

        assertThat(result).isEqualByComparingTo("0.6");
    }

    @Test
    @DisplayName("버킷 저장 횟수가 음수이면 예외가 발생한다.")
    void calculateBucketScoreNegativeCountThrowsException() {
        assertThatThrownBy(() -> calculator.calculateBucketScore(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("리뷰 별점이 5점이면 0.2점이다.")
    void calculateReviewScoreFiveStars() {
        BigDecimal result = calculator.calculateReviewScore(5);

        assertThat(result).isEqualByComparingTo("0.2");
    }

    @Test
    @DisplayName("리뷰 별점이 3점이면 0점이다.")
    void calculateReviewScoreThreeStars() {
        BigDecimal result = calculator.calculateReviewScore(3);

        assertThat(result).isEqualByComparingTo("0.0");
    }

    @Test
    @DisplayName("리뷰 별점이 1점이면 -0.2점이다.")
    void calculateReviewScoreOneStar() {
        BigDecimal result = calculator.calculateReviewScore(1);

        assertThat(result).isEqualByComparingTo("-0.2");
    }

    @Test
    @DisplayName("재참여 의향이 있으면 0.1점이다.")
    void calculateRejoinScoreYes() {
        BigDecimal result = calculator.calculateRejoinScore(true);

        assertThat(result).isEqualByComparingTo("0.1");
    }

    @Test
    @DisplayName("재참여 의향이 없으면 -0.1점이다.")
    void calculateRejoinScoreNo() {
        BigDecimal result = calculator.calculateRejoinScore(false);

        assertThat(result).isEqualByComparingTo("-0.1");
    }

    @Test
    @DisplayName("재참여 의향이 null이면 0점이다.")
    void calculateRejoinScoreNull() {
        BigDecimal result = calculator.calculateRejoinScore(null);

        assertThat(result).isEqualByComparingTo("0");
    }
    @Test
    @DisplayName("버킷 저장은 카테고리별로 2회까지만 반영하고 오늘의 행동은 감쇠되지 않는다.")
    void calculateBucketScores() {
        Mission mission = mock(Mission.class);

        when(mission.getCategory()).thenReturn(Category.FOOD_CAFE);

        BucketItem first = mock(BucketItem.class);
        when(first.getMission()).thenReturn(mission);
        when(first.getSavedAt()).thenReturn(LocalDateTime.of(2026, 11, 3, 10, 0));

        BucketItem second = mock(BucketItem.class);
        when(second.getMission()).thenReturn(mission);
        when(second.getSavedAt()).thenReturn(LocalDateTime.of(2026, 11, 3, 11, 0));

        BucketItem third = mock(BucketItem.class);
        when(third.getMission()).thenReturn(mission);
        when(third.getSavedAt()).thenReturn(LocalDateTime.of(2026, 11, 3, 12, 0));

        Map<Category, BigDecimal> result = calculator.calculateBucketScores(List.of(first, second, third), LocalDate.of(2026, 11, 3));

        assertThat(result.get(Category.FOOD_CAFE)).isEqualByComparingTo("0.6");
    }

    @Test
    @DisplayName("5점 리뷰와 재참여 의향은 각각의 행동 점수를 합산한다.")
    void calculateReviewScores() {
        Mission mission = mock(Mission.class);
        when(mission.getCategory()).thenReturn(Category.FOOD_CAFE);

        BucketItem bucketItem = mock(BucketItem.class);
        when(bucketItem.getMission()).thenReturn(mission);

        Schedule schedule = mock(Schedule.class);
        when(schedule.getBucketItem()).thenReturn(bucketItem);

        Review review = mock(Review.class);
        when(review.getSchedule()).thenReturn(schedule);
        when(review.getStarRating()).thenReturn(5);
        when(review.getIsRejoinIntended()).thenReturn(true);
        when(review.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 11, 3, 10, 0));

        Map<Category, BigDecimal> result = calculator.calculateReviewScores(List.of(review), LocalDate.of(2026, 11, 3));

        assertThat(result.get(Category.FOOD_CAFE)).isEqualByComparingTo("0.3");
    }

    @Test
    @DisplayName("카테고리별 최종 행동 점수는 최대 0.6까지 제한된다.")
    void calculateCategoryBehaviorScoresMaximum() {
        Mission mission = mock(Mission.class);
        when(mission.getCategory()).thenReturn(Category.FOOD_CAFE);

        BucketItem first = mock(BucketItem.class);
        when(first.getMission()).thenReturn(mission);
        when(first.getSavedAt()).thenReturn(LocalDateTime.of(2026, 11, 3, 10, 0));

        BucketItem second = mock(BucketItem.class);
        when(second.getMission()).thenReturn(mission);
        when(second.getSavedAt()).thenReturn(LocalDateTime.of(2026, 11, 3, 11, 0));

        Schedule schedule = mock(Schedule.class);
        when(schedule.getBucketItem()).thenReturn(first);

        Review review = mock(Review.class);
        when(review.getSchedule()).thenReturn(schedule);
        when(review.getStarRating()).thenReturn(5);
        when(review.getIsRejoinIntended()).thenReturn(true);
        when(review.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 11, 3, 12, 0));

        Map<Category, BigDecimal> result = calculator.calculateCategoryBehaviorScores(List.of(first, second), List.of(review), LocalDate.of(2026, 11, 3));

        assertThat(result.get(Category.FOOD_CAFE)).isEqualByComparingTo("0.6");
    }
}