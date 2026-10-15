package com.kiri.server.domain.mission.generator;

import com.kiri.server.domain.external.converter.SourceTextParser;
import com.kiri.server.domain.external.dto.NormalizedContent;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.ChecklistSource;
import com.kiri.server.domain.mission.enums.ItemType;
import com.kiri.server.domain.mission.generator.dto.MissionDraft.ChecklistDraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChecklistPlanner {

    public static final int MIN_STEPS = 2;
    private static final int MAX_STEPS = 3;
    private static final int MAX_DESCRIPTION_LENGTH = 255;
    private static final String MENU_DESCRIPTION = "이곳의 대표 메뉴예요.";
    private static final String PROGRAM_DESCRIPTION = "공식 안내에 있는 프로그램이에요.";

    private static final Map<Category, List<Step>> DEFAULT_STEPS = Map.of(
            Category.EXHIBITION, List.of(
                    new Step("마음에 드는 작품 고르기", "관람하면서 각자 가장 마음에 드는 작품을 하나씩 골라보세요."),
                    new Step("서로 감상 이야기하기", "고른 작품과 인상 깊었던 점을 서로 이야기해보세요.")),
            Category.POPUP, List.of(
                    new Step("함께 참여할 부스나 프로그램 고르기", "둘러보기 전에 가족이 함께 참여할 부스나 프로그램을 골라보세요."),
                    new Step("가장 재미있었던 활동 공유하기", "가장 재미있었던 활동을 서로 이야기해보세요.")),
            Category.PERFORMANCE, List.of(
                    new Step("관람 전 기대되는 점 이야기하기", "공연을 보기 전에 무엇이 기대되는지 서로 이야기해보세요."),
                    new Step("가장 기억에 남는 장면 이야기하기", "공연이 끝난 뒤 가장 기억에 남는 장면을 서로 이야기해보세요.")),
            Category.FOOD_CAFE, List.of(
                    new Step("각자 먹고 싶은 메뉴 고르기", "가족이 각자 먹고 싶은 메뉴를 하나씩 골라보세요."),
                    new Step("가장 마음에 들었던 메뉴 이야기하기", "식사 후 가장 마음에 들었던 메뉴를 서로 이야기해보세요.")),
            Category.OUTDOOR, List.of(
                    new Step("함께 걸을 코스 정하기", "출발 전에 가족이 함께 걸을 코스를 정해보세요."),
                    new Step("서로 근황 이야기하기", "걸으면서 요즘 있었던 일을 서로 이야기해보세요.")),
            Category.EXPERIENCE, List.of(
                    new Step("함께할 체험 고르기", "가족이 함께할 체험을 골라보세요."),
                    new Step("가장 재미있었던 순간 공유하기", "체험 후 가장 재미있었던 순간을 서로 이야기해보세요.")));

    public static List<ChecklistDraft> plan(Category category, NormalizedContent content) {
        Map<String, Step> origin = new LinkedHashMap<>();
        if (category == Category.FOOD_CAFE) {
            for (String menu : content.menuNames()) {
                putIfRoom(origin, new Step(menu + " 함께 맛보기", MENU_DESCRIPTION));
            }
        }
        for (String text : content.programTexts()) {
            for (String program : SourceTextParser.splitItems(text)) {
                putIfRoom(origin, new Step(program, PROGRAM_DESCRIPTION));
            }
        }

        List<ChecklistDraft> drafts = new ArrayList<>();
        int order = 1;
        for (Step step : origin.values()) {
            drafts.add(draft(ChecklistSource.ORIGIN, step, order++));
        }
        for (Step step : DEFAULT_STEPS.getOrDefault(category, List.of())) {
            if (drafts.size() >= MIN_STEPS) {
                break;
            }
            drafts.add(draft(ChecklistSource.KIRI, step, order++));
        }
        return drafts;
    }

    private static void putIfRoom(Map<String, Step> steps, Step step) {
        if (steps.size() < MAX_STEPS) {
            steps.putIfAbsent(step.title(), step);
        }
    }

    private static ChecklistDraft draft(ChecklistSource source, Step step, int order) {
        String description = step.description().length() <= MAX_DESCRIPTION_LENGTH
                ? step.description()
                : step.description().substring(0, MAX_DESCRIPTION_LENGTH);
        return new ChecklistDraft(ItemType.STEP, source, step.title(), description, order);
    }

    private record Step(String title, String description) {
    }
}
