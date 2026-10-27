package com.kiri.server.domain.mission.generator;

import com.kiri.server.domain.external.converter.SourceTextParser;
import com.kiri.server.domain.external.converter.SourceTextParser.MinuteRange;
import com.kiri.server.domain.mission.enums.Category;

import java.util.Map;

public class ActivityEstimator {

    private static final Map<Category, int[]> DEFAULT_MINUTES = Map.of(
            Category.EXHIBITION, new int[]{60, 120},
            Category.POPUP, new int[]{60, 180},
            Category.PERFORMANCE, new int[]{90, 150},
            Category.FOOD_CAFE, new int[]{60, 90},
            Category.OUTDOOR, new int[]{60, 180},
            Category.EXPERIENCE, new int[]{60, 180});

    private static final Map<Category, int[]> DEFAULT_STEPS = Map.of(
            Category.EXHIBITION, new int[]{2000, 5000},
            Category.POPUP, new int[]{3000, 8000},
            Category.PERFORMANCE, new int[]{500, 2000},
            Category.FOOD_CAFE, new int[]{300, 1500},
            Category.OUTDOOR, new int[]{5000, 12000},
            Category.EXPERIENCE, new int[]{2000, 6000});

    public static Estimate estimate(Category category, String durationText) {
        MinuteRange duration = SourceTextParser.parseDurationMinutes(durationText);
        int[] minutes = duration != null
                ? new int[]{duration.min(), duration.max()}
                : DEFAULT_MINUTES.get(category);
        int[] steps = DEFAULT_STEPS.get(category);
        return new Estimate(minutes[0], minutes[1], steps[0], steps[1]);
    }

    public record Estimate(int durationMinMinutes, int durationMaxMinutes, int stepsMin, int stepsMax) {
    }
}
