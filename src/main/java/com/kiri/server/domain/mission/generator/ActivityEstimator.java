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

    public static Estimate estimate(Category category, String durationText) {
        MinuteRange duration = SourceTextParser.parseDurationMinutes(durationText);
        int[] minutes = duration != null
                ? new int[]{duration.min(), duration.max()}
                : DEFAULT_MINUTES.get(category);
        return new Estimate(minutes[0], minutes[1]);
    }

    public record Estimate(int durationMinMinutes, int durationMaxMinutes) {
    }
}
