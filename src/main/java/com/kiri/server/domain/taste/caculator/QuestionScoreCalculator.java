package com.kiri.server.domain.taste.caculator;

import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.question.entity.MemberAnswer;
import com.kiri.server.domain.question.entity.MemberAnswerCategory;
import com.kiri.server.domain.taste.enums.ActivityLevel;
import com.kiri.server.domain.taste.enums.DurationLevel;
import com.kiri.server.domain.taste.enums.PriceLevel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class QuestionScoreCalculator {

    private static final BigDecimal TWO = new BigDecimal("2");
    private static final BigDecimal ONE = BigDecimal.ONE;

    private static final int SCALE = 10;

    public BigDecimal calculateAnswerWeight(long daysAgo) {
        if (daysAgo < 0) {
            throw new IllegalArgumentException("지난 일수는 음수일 수 없습니다.");
        }

        double exponent = (double) daysAgo / 30.0;
        double weight = Math.pow(0.5, exponent);

        return BigDecimal.valueOf(weight).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateReliability(BigDecimal questionWeightSum) {
        if (questionWeightSum == null) {
            throw new IllegalArgumentException("질문 무게 합은 null일 수 없습니다.");
        }

        if (questionWeightSum.signum() < 0) {
            throw new IllegalArgumentException("질문 무게 합은 음수일 수 없습니다.");
        }

        return questionWeightSum.divide(TWO, SCALE, RoundingMode.HALF_UP).min(ONE);
    }

    public <E extends Enum<E>> BigDecimal calculateValueScore(List<MemberAnswer> answers, LocalDate today, E targetValue, Function<MemberAnswer, E> valueExtractor) {
        BigDecimal totalWeight = calculateQuestionWeightSum(answers, today);

        if (totalWeight.signum() == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal targetWeight = answers.stream()
                .filter(answer -> targetValue.equals(valueExtractor.apply(answer)))
                .map(answer -> calculateAnswerWeight(calculateDaysAgo(answer, today)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return targetWeight.divide(totalWeight, SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateQuestionWeightSum(List<MemberAnswer> answers, LocalDate today) {
        return answers.stream()
                .map(answer -> calculateAnswerWeight(calculateDaysAgo(answer, today)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateReliability(List<MemberAnswer> answers, LocalDate today) {
        BigDecimal questionWeightSum = calculateQuestionWeightSum(answers, today);

        return calculateReliability(questionWeightSum);
    }

    private long calculateDaysAgo(MemberAnswer answer, LocalDate today) {
        return ChronoUnit.DAYS.between(answer.getCreatedAt().toLocalDate(), today);
    }

    public BigDecimal calculateIndoorOutdoorScore(List<MemberAnswer> answers, LocalDate today, IndoorOutdoor target) {
        return calculateValueScore(answers, today, target, MemberAnswer::getIndoorOutdoor);
    }

    public BigDecimal calculatePriceScore(List<MemberAnswer> answers, LocalDate today, PriceLevel target) {
        return calculateValueScore(answers, today, target, MemberAnswer::getPriceLevel);
    }

    public BigDecimal calculateDurationScore(List<MemberAnswer> answers, LocalDate today, DurationLevel target) {
        return calculateValueScore(answers, today, target, MemberAnswer::getDurationLevel);
    }

    public BigDecimal calculateActivityLevelScore(List<MemberAnswer> answers, LocalDate today, ActivityLevel target) {
        return calculateValueScore(answers, today, target, MemberAnswer::getActivityLevel);
    }

    public Map<Category, BigDecimal> calculateCategoryScores(List<MemberAnswer> answers, List<MemberAnswerCategory> answerCategories, LocalDate today) {
        Map<Category, BigDecimal> categoryWeights = new EnumMap<>(Category.class);

        for (MemberAnswerCategory answerCategory : answerCategories) {
            MemberAnswer answer = answerCategory.getMemberAnswer();

            BigDecimal weight = calculateAnswerWeight(calculateDaysAgo(answer, today));

            categoryWeights.merge(answerCategory.getCategory(), weight, BigDecimal::add);
        }

        return categoryWeights.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), entry.getValue().min(BigDecimal.ONE)))
                .sorted(Map.Entry.<Category, BigDecimal>comparingByValue().reversed())
                .limit(3)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first, () -> new EnumMap<>(Category.class)));
    }
}
