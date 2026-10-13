package com.kiri.server.domain.mission.generator;

import com.kiri.server.domain.external.converter.SourceTextParser;
import com.kiri.server.domain.external.dto.NormalizedContent;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.ItemType;
import com.kiri.server.domain.mission.generator.dto.MissionDraft.ChecklistDraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChecklistPlanner {

    private static final int MIN_STEPS = 2;
    private static final int MAX_STEPS = 3;
    private static final int MAX_DESCRIPTION_LENGTH = 255;
    private static final String EVENT_TYPE = "15";

    public static List<ChecklistDraft> plan(Category category, NormalizedContent content) {
        Map<String, String> steps = new LinkedHashMap<>();

        if (category == Category.FOOD_CAFE) {
            for (String menu : content.menuNames()) {
                putIfRoom(steps, menu + " 함께 맛보기", "이곳의 대표 메뉴예요.");
            }
        }
        for (String text : content.programTexts()) {
            for (String program : SourceTextParser.splitItems(text)) {
                putIfRoom(steps, program, "공식 안내에 있는 프로그램이에요.");
            }
        }

        if (steps.size() < MIN_STEPS) {
            putIfRoom(steps, "가족 사진 남기기", "오늘의 순간을 가족 사진으로 남겨보세요.");
        }
        if (steps.size() < MIN_STEPS) {
            putIfRoom(steps, "주변 함께 둘러보기", aroundName(content) + " 주변을 천천히 함께 둘러보세요.");
        }

        List<ChecklistDraft> drafts = new ArrayList<>();
        int order = 1;
        for (Map.Entry<String, String> step : steps.entrySet()) {
            drafts.add(new ChecklistDraft(ItemType.STEP, step.getKey(), limit(step.getValue()), order++));
        }
        return drafts;
    }

    private static String aroundName(NormalizedContent content) {
        boolean eventWithoutVenue = EVENT_TYPE.equals(content.contentTypeId())
                && (content.venueName() == null || content.venueName().isBlank());
        return eventWithoutVenue ? "행사장" : content.placeName();
    }

    private static void putIfRoom(Map<String, String> steps, String title, String description) {
        if (steps.size() < MAX_STEPS) {
            steps.putIfAbsent(title, description);
        }
    }

    private static String limit(String text) {
        return text.length() <= MAX_DESCRIPTION_LENGTH ? text : text.substring(0, MAX_DESCRIPTION_LENGTH);
    }
}
