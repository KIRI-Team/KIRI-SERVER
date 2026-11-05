package com.kiri.server.domain.taste.caculator;

import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.review.entity.Review;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class BehaviorScoreCalculator {

    private static final BigDecimal BUCKET_SCORE = new BigDecimal("0.3");
    private static final BigDecimal REVIEW_BASE = new BigDecimal("3");
    private static final BigDecimal REVIEW_WEIGHT = new BigDecimal("0.1");
    private static final BigDecimal REJOIN_YES_SCORE = new BigDecimal("0.1");
    private static final BigDecimal REJOIN_NO_SCORE = new BigDecimal("-0.1");
    private static final BigDecimal MIN_BEHAVIOR_SCORE = new BigDecimal("-0.3");
    private static final BigDecimal MAX_BEHAVIOR_SCORE = new BigDecimal("0.6");
    private static final int MAX_BUCKET_COUNT = 2;

    private static final int SCALE = 10;

    public BigDecimal calculateBehaviorWeight(long daysAgo) {
        if (daysAgo < 0) {
            throw new IllegalArgumentException("지난 일수는 음수일 수 없습니다.");
        }

        double exponent = (double) daysAgo / 90.0;
        double weight = Math.pow(0.5, exponent);

        return BigDecimal.valueOf(weight).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateBucketScore(long bucketCount) {
        if (bucketCount < 0) {
            throw new IllegalArgumentException("버킷 저장 횟수는 음수일 수 없습니다.");
        }

        long count = Math.min(bucketCount, MAX_BUCKET_COUNT);

        return BUCKET_SCORE.multiply(BigDecimal.valueOf(count));
    }

    public BigDecimal calculateReviewScore(Integer starRating) {
        if (starRating == null) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(starRating).subtract(REVIEW_BASE).multiply(REVIEW_WEIGHT);
    }

    public BigDecimal calculateRejoinScore(Boolean rejoinIntended) {
        if (Boolean.TRUE.equals(rejoinIntended)) {
            return REJOIN_YES_SCORE;
        }

        if (Boolean.FALSE.equals(rejoinIntended)) {
            return REJOIN_NO_SCORE;
        }

        return BigDecimal.ZERO;
    }

    public Map<Category, BigDecimal> calculateBucketScores(List<BucketItem> bucketItems, LocalDate today) {
        Map<Category, BigDecimal> scores = new EnumMap<>(Category.class);

        Map<Category, Integer> counts = new EnumMap<>(Category.class);

        for (BucketItem bucketItem : bucketItems) {
            Category category = bucketItem.getMission().getCategory();

            int count = counts.getOrDefault(category, 0);

            if (count >= MAX_BUCKET_COUNT) {
                continue;
            }

            long daysAgo = ChronoUnit.DAYS.between(bucketItem.getSavedAt().toLocalDate(), today);

            BigDecimal weight = calculateBehaviorWeight(daysAgo);

            BigDecimal score = BUCKET_SCORE.multiply(weight);

            scores.merge(category, score, BigDecimal::add);

            counts.put(category, count + 1);
        }

        return scores;
    }

    public Map<Category, BigDecimal> calculateReviewScores(List<Review> reviews, LocalDate today) {
        Map<Category, BigDecimal> scores = new EnumMap<>(Category.class);

        for (Review review : reviews) {
            Category category = review.getSchedule().getBucketItem().getMission().getCategory();

            BigDecimal score = calculateReviewScore(review.getStarRating()).add(calculateRejoinScore(review.getIsRejoinIntended()));

            long daysAgo = ChronoUnit.DAYS.between(review.getCreatedAt().toLocalDate(), today);

            BigDecimal weight = calculateBehaviorWeight(daysAgo);

            score = score.multiply(weight);

            scores.merge(category, score, BigDecimal::add);
        }

        return scores;
    }

    public Map<Category, BigDecimal> calculateCategoryBehaviorScores(List<BucketItem> bucketItems, List<Review> reviews, LocalDate today) {
        Map<Category, BigDecimal> scores = new EnumMap<>(Category.class);

        calculateBucketScores(bucketItems, today).forEach((category, score) -> scores.merge(category, score, BigDecimal::add));

        calculateReviewScores(reviews, today).forEach((category, score) -> scores.merge(category, score, BigDecimal::add));

        scores.replaceAll((category, score) -> score.max(MIN_BEHAVIOR_SCORE).min(MAX_BEHAVIOR_SCORE));

        return scores;
    }
}
