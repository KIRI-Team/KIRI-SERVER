package com.kiri.server.domain.mission.generator;

import com.kiri.server.domain.external.dto.NormalizedContent;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.WalkingLevel;

import java.util.regex.Pattern;

public class WalkingLevelClassifier {

    private static final Pattern SEDENTARY = Pattern.compile("앉아|착석|좌석|관람석");

    public static WalkingLevel classify(Category category, NormalizedContent content) {
        if (category == null) {
            return null;
        }
        if (hasSedentaryCue(content)) {
            return WalkingLevel.LOW;
        }
        return byCategory(category);
    }

    private static WalkingLevel byCategory(Category category) {
        return switch (category) {
            case PERFORMANCE, FOOD_CAFE -> WalkingLevel.LOW;
            case EXHIBITION, POPUP, EXPERIENCE -> WalkingLevel.MEDIUM;
            case OUTDOOR -> WalkingLevel.HIGH;
        };
    }

    private static boolean hasSedentaryCue(NormalizedContent content) {
        if (content == null) {
            return false;
        }
        if (matches(content.title()) || matches(content.overview())) {
            return true;
        }
        return content.programTexts().stream().anyMatch(WalkingLevelClassifier::matches);
    }

    private static boolean matches(String text) {
        return text != null && SEDENTARY.matcher(text).find();
    }
}
