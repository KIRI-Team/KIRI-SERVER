package com.kiri.server.domain.taste.caculator;

import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.question.entity.MemberAnswer;
import com.kiri.server.domain.question.entity.MemberAnswerCategory;
import com.kiri.server.domain.review.entity.Review;
import com.kiri.server.domain.taste.dto.TasteSourceDTO;
import com.kiri.server.domain.taste.entity.MemberTaste;
import com.kiri.server.domain.taste.enums.ActivityLevel;
import com.kiri.server.domain.taste.enums.DurationLevel;
import com.kiri.server.domain.taste.enums.PriceLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MemberTasteScoreCalculator {

    private static final BigDecimal PREFERENCE_ACTIVITY_WEIGHT = new BigDecimal("0.40");
    private static final BigDecimal SPACE_WEIGHT = new BigDecimal("0.23");
    private static final BigDecimal PRICE_WEIGHT = new BigDecimal("0.16");
    private static final BigDecimal DURATION_WEIGHT = new BigDecimal("0.12");
    private static final BigDecimal ACTIVITY_LEVEL_WEIGHT = new BigDecimal("0.09");
    private static final BigDecimal OPPOSITE_MATCH_SCORE = new BigDecimal("0.5");
    private static final BigDecimal LOW_PRICE_LIMIT = new BigDecimal("15000");

    private static final int SCALE = 10;

    private final QuestionScoreCalculator questionScoreCalculator;
    private final PreferenceWeightCalculator preferenceWeightCalculator;
    private final BehaviorScoreCalculator behaviorScoreCalculator;

    public TasteSourceDTO.MemberScore calculate(MemberTaste memberTaste, List<MemberAnswer> answers, List<MemberAnswerCategory> answerCategories, List<BucketItem> bucketItems, List<Review> reviews, Mission mission, LocalDate today) {
        BigDecimal questionReliability = questionScoreCalculator.calculateReliability(answers, today);

        TasteSourceDTO.PreferenceWeight preferenceWeight = preferenceWeightCalculator.calculate(questionReliability, !answers.isEmpty(), memberTaste != null);

        Map<Category, BigDecimal> behaviorScores = behaviorScoreCalculator.calculateCategoryBehaviorScores(bucketItems, reviews, today);

        BigDecimal preferenceActivity = calculatePreferenceActivityScore(memberTaste, answers, answerCategories, preferenceWeight, behaviorScores, mission, today);

        BigDecimal space = calculateSpaceScore(memberTaste, answers, preferenceWeight, mission, today);

        BigDecimal price = calculatePriceScore(memberTaste, answers, preferenceWeight, mission, today);

        BigDecimal duration = calculateDurationScore(memberTaste, answers, preferenceWeight, mission, today);

        BigDecimal activityLevel = calculateActivityLevelScore(memberTaste, answers, preferenceWeight, mission, today);

        BigDecimal total = preferenceActivity.multiply(PREFERENCE_ACTIVITY_WEIGHT)
                .add(space.multiply(SPACE_WEIGHT))
                .add(price.multiply(PRICE_WEIGHT))
                .add(duration.multiply(DURATION_WEIGHT))
                .add(activityLevel.multiply(ACTIVITY_LEVEL_WEIGHT))
                .setScale(SCALE, RoundingMode.HALF_UP);

        return new TasteSourceDTO.MemberScore(preferenceActivity, space, price, duration, activityLevel, total);
    }

    private BigDecimal calculatePreferenceActivityScore(MemberTaste memberTaste, List<MemberAnswer> answers, List<MemberAnswerCategory> answerCategories, TasteSourceDTO.PreferenceWeight preferenceWeight, Map<Category, BigDecimal> behaviorScores, Mission mission, LocalDate today) {
        Category missionCategory = mission.getCategory();

        Map<Category, BigDecimal> questionScores = questionScoreCalculator.calculateCategoryScores(answers, answerCategories, today);

        BigDecimal questionScore = questionScores.getOrDefault(missionCategory, BigDecimal.ZERO);

        BigDecimal tasteTestScore = BigDecimal.ZERO;

        if (memberTaste != null && memberTaste.getCategoryValues().contains(missionCategory)) {
            tasteTestScore = BigDecimal.ONE;
        }

        BigDecimal preferenceScore = combinePreference(questionScore, tasteTestScore, preferenceWeight);

        BigDecimal behaviorScore = behaviorScores.getOrDefault(missionCategory, BigDecimal.ZERO);

        return preferenceScore.add(behaviorScore);
    }

    private BigDecimal calculateSpaceScore(MemberTaste memberTaste, List<MemberAnswer> answers, TasteSourceDTO.PreferenceWeight preferenceWeight, Mission mission, LocalDate today) {

        if (mission.getIndoorOutdoor() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal indoorQuestion = questionScoreCalculator.calculateIndoorOutdoorScore(answers, today, IndoorOutdoor.INDOOR);

        BigDecimal outdoorQuestion = questionScoreCalculator.calculateIndoorOutdoorScore(answers, today, com.kiri.server.domain.mission.enums.IndoorOutdoor.OUTDOOR);

        BigDecimal indoorTasteTest = calculateIndoorOutdoorTasteTestScore(memberTaste, IndoorOutdoor.INDOOR);

        BigDecimal outdoorTasteTest = calculateIndoorOutdoorTasteTestScore(memberTaste, IndoorOutdoor.OUTDOOR);

        BigDecimal indoorPreference = combinePreference(indoorQuestion, indoorTasteTest, preferenceWeight);

        BigDecimal outdoorPreference = combinePreference(outdoorQuestion, outdoorTasteTest, preferenceWeight);

        BigDecimal indoorMatch = calculateSpaceMatch(IndoorOutdoor.INDOOR, mission.getIndoorOutdoor());

        BigDecimal outdoorMatch = calculateSpaceMatch(IndoorOutdoor.OUTDOOR, mission.getIndoorOutdoor());

        return indoorPreference
                .multiply(indoorMatch)
                .add(outdoorPreference.multiply(outdoorMatch))
                .min(BigDecimal.ONE);
    }

    private BigDecimal calculateIndoorOutdoorTasteTestScore(MemberTaste memberTaste, IndoorOutdoor target) {

        if (memberTaste == null || memberTaste.getIndoorOutdoor() == null) {
            return BigDecimal.ZERO;
        }

        IndoorOutdoor preference = memberTaste.getIndoorOutdoor();

        if (preference == IndoorOutdoor.BOTH) {
            return BigDecimal.ONE;
        }

        return preference == target ? BigDecimal.ONE : BigDecimal.ZERO;
    }

    private BigDecimal calculateSpaceMatch(IndoorOutdoor preference, IndoorOutdoor missionValue) {

        if (missionValue == null) {
            return BigDecimal.ZERO;
        }

        if (missionValue == IndoorOutdoor.BOTH) {
            return BigDecimal.ONE;
        }

        return preference == missionValue ? BigDecimal.ONE : OPPOSITE_MATCH_SCORE;
    }

    private BigDecimal calculatePriceScore(MemberTaste memberTaste, List<MemberAnswer> answers, TasteSourceDTO.PreferenceWeight preferenceWeight, Mission mission, LocalDate today) {

        if (mission.getPrice() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal freeQuestion = questionScoreCalculator.calculatePriceScore(answers, today, PriceLevel.FREE);

        BigDecimal lowQuestion = questionScoreCalculator.calculatePriceScore(answers, today, PriceLevel.LOW);

        BigDecimal freeTasteTest = memberTaste != null && memberTaste.getPriceLevel() == PriceLevel.FREE ? BigDecimal.ONE : BigDecimal.ZERO;

        BigDecimal lowTasteTest = memberTaste != null && memberTaste.getPriceLevel() == PriceLevel.LOW ? BigDecimal.ONE : BigDecimal.ZERO;

        BigDecimal freePreference = combinePreference(freeQuestion, freeTasteTest, preferenceWeight);

        BigDecimal lowPreference = combinePreference(lowQuestion, lowTasteTest, preferenceWeight);

        BigDecimal freeMatch = calculatePriceMatch(PriceLevel.FREE, mission.getPrice());

        BigDecimal lowMatch = calculatePriceMatch(PriceLevel.LOW, mission.getPrice());

        return freePreference
                .multiply(freeMatch)
                .add(lowPreference.multiply(lowMatch))
                .min(BigDecimal.ONE);
    }

    private BigDecimal calculatePriceMatch(PriceLevel preference, Integer missionPrice) {

        if (missionPrice == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal price = BigDecimal.valueOf(missionPrice);

        if (preference == PriceLevel.FREE) {
            if (missionPrice == 0) {
                return BigDecimal.ONE;
            }

            if (price.compareTo(LOW_PRICE_LIMIT) <= 0) {
                return new BigDecimal("0.3");
            }

            return new BigDecimal("0.5");
        }

        if (price.compareTo(LOW_PRICE_LIMIT) <= 0) {
            return BigDecimal.ONE;
        }

        return new BigDecimal("0.3");
    }


    private BigDecimal calculateDurationScore(MemberTaste memberTaste, List<MemberAnswer> answers, TasteSourceDTO.PreferenceWeight preferenceWeight, Mission mission, LocalDate today) {

        if (mission.getDurationMinMinutes() == null || mission.getDurationMaxMinutes() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal withinOneHourQuestion = questionScoreCalculator.calculateDurationScore(answers, today, DurationLevel.WITHIN_1H);

        BigDecimal oneToThreeQuestion = questionScoreCalculator.calculateDurationScore(answers, today, DurationLevel.H1_TO_3);

        BigDecimal halfDayPlusQuestion = questionScoreCalculator.calculateDurationScore(answers, today, DurationLevel.HALF_DAY_PLUS);

        BigDecimal withinOneHourTasteTest = memberTaste != null && memberTaste.getDurationLevel() == DurationLevel.WITHIN_1H ? BigDecimal.ONE : BigDecimal.ZERO;

        BigDecimal oneToThreeTasteTest = memberTaste != null && memberTaste.getDurationLevel() == DurationLevel.H1_TO_3 ? BigDecimal.ONE : BigDecimal.ZERO;

        BigDecimal halfDayPlusTasteTest = memberTaste != null && memberTaste.getDurationLevel() == DurationLevel.HALF_DAY_PLUS ? BigDecimal.ONE : BigDecimal.ZERO;

        BigDecimal withinOneHourPreference = combinePreference(withinOneHourQuestion, withinOneHourTasteTest, preferenceWeight);

        BigDecimal oneToThreePreference = combinePreference(oneToThreeQuestion, oneToThreeTasteTest, preferenceWeight);

        BigDecimal halfDayPlusPreference = combinePreference(halfDayPlusQuestion, halfDayPlusTasteTest, preferenceWeight);

        BigDecimal withinOneHourMatch = calculateDurationMatch(DurationLevel.WITHIN_1H, mission.getDurationMinMinutes(), mission.getDurationMaxMinutes());

        BigDecimal oneToThreeMatch = calculateDurationMatch(DurationLevel.H1_TO_3, mission.getDurationMinMinutes(), mission.getDurationMaxMinutes());

        BigDecimal halfDayPlusMatch = calculateDurationMatch(DurationLevel.HALF_DAY_PLUS, mission.getDurationMinMinutes(), mission.getDurationMaxMinutes());

        return withinOneHourPreference.multiply(withinOneHourMatch)
                .add(oneToThreePreference.multiply(oneToThreeMatch))
                .add(halfDayPlusPreference.multiply(halfDayPlusMatch))
                .min(BigDecimal.ONE);
    }

    private BigDecimal calculateDurationMatch(DurationLevel preference, Integer missionMin, Integer missionMax) {

        if (missionMin == null || missionMax == null) {
            return BigDecimal.ZERO;
        }

        Integer preferenceMin = preference.getMinMinutes();

        Integer preferenceMax = preference.getMaxMinutes();

        boolean overlap;

        if (preferenceMax == null) {
            overlap = missionMax >= preferenceMin;
        } else {
            overlap = missionMax >= preferenceMin && missionMin <= preferenceMax;
        }

        return overlap ? BigDecimal.ONE : OPPOSITE_MATCH_SCORE;
    }


    private BigDecimal calculateActivityLevelScore(MemberTaste memberTaste, List<MemberAnswer> answers, TasteSourceDTO.PreferenceWeight preferenceWeight, Mission mission, LocalDate today) {
        BigDecimal lightQuestion = questionScoreCalculator.calculateActivityLevelScore(answers, today, ActivityLevel.LIGHT);

        BigDecimal moderateQuestion = questionScoreCalculator.calculateActivityLevelScore(answers, today, ActivityLevel.MODERATE);

        BigDecimal activeQuestion = questionScoreCalculator.calculateActivityLevelScore(answers, today, ActivityLevel.ACTIVE);

        BigDecimal lightTasteTest = memberTaste != null && memberTaste.getActivityLevel() == ActivityLevel.LIGHT ? BigDecimal.ONE : BigDecimal.ZERO;

        BigDecimal moderateTasteTest = memberTaste != null && memberTaste.getActivityLevel() == ActivityLevel.MODERATE ? BigDecimal.ONE : BigDecimal.ZERO;

        BigDecimal activeTasteTest = memberTaste != null && memberTaste.getActivityLevel() == ActivityLevel.ACTIVE ? BigDecimal.ONE : BigDecimal.ZERO;

        BigDecimal lightPreference = combinePreference(lightQuestion, lightTasteTest, preferenceWeight);

        BigDecimal moderatePreference = combinePreference(moderateQuestion, moderateTasteTest, preferenceWeight);

        BigDecimal activePreference = combinePreference(activeQuestion, activeTasteTest, preferenceWeight);

        BigDecimal lightMatch = calculateActivityLevelMatch(ActivityLevel.LIGHT, mission);

        BigDecimal moderateMatch = calculateActivityLevelMatch(ActivityLevel.MODERATE, mission);

        BigDecimal activeMatch = calculateActivityLevelMatch(ActivityLevel.ACTIVE, mission);

        return lightPreference.multiply(lightMatch)
                .add(moderatePreference.multiply(moderateMatch))
                .add(activePreference.multiply(activeMatch))
                .min(BigDecimal.ONE);
    }

    private BigDecimal calculateActivityLevelMatch(ActivityLevel preference, Mission mission) {
        /*
         * 현재 프로젝트에는
         * ActivityLevel(LIGHT/MODERATE/ACTIVE)과
         * Mission.estimatedStepsMin/Max를 연결하는
         * 걸음 수 기준이 정의되어 있지 않다.
         *
         * 임의의 기준을 만들어 점수를 왜곡하지 않기 위해
         * 기준 확정 전까지는 0으로 둔다.
         */
        return BigDecimal.ZERO;
    }


    private BigDecimal combinePreference(BigDecimal questionScore, BigDecimal tasteTestScore, TasteSourceDTO.PreferenceWeight preferenceWeight) {
        return questionScore
                .multiply(preferenceWeight.questionWeight())
                .add(tasteTestScore.multiply(preferenceWeight.tasteTestWeight()));
    }
}
