package com.kiri.server.domain.mission.generator;

import com.kiri.server.domain.external.converter.SourceTextParser;
import com.kiri.server.domain.mission.enums.ChecklistSource;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.mission.enums.ItemType;
import com.kiri.server.domain.mission.enums.WalkingLevel;
import com.kiri.server.domain.mission.generator.dto.MissionDraft.ChecklistDraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 미션 근거(공식 준비물/걷기량/실내외/활동시간)으로 맞춤템을 만듦
public class EssentialPlanner {

    public static final int MAX_ESSENTIALS = 4;
    static final int LONG_OUTDOOR_MINUTES = 180;
    private static final int MAX_ITEM_LENGTH = 20;
    private static final String OFFICIAL_DESCRIPTION = "공식 안내에 있는 준비물이에요.";
    private static final Essential COMFORTABLE_SHOES = new Essential("편한 신발", "많이 걷는 일정이에요.");
    private static final Essential WATER = new Essential("물", "야외에 오래 머무는 일정이에요.");

    private static final Pattern SENTENCE_BREAK = Pattern.compile("\\n|(?<=[.!?])\\s+");
    private static final Pattern LABELED = Pattern.compile("(?:준비물|지참물)\\s*[:：]\\s*(.+)");
    private static final Pattern BRING = Pattern.compile(
            "((?:[가-힣A-Za-z0-9]{2,}\\s)?[가-힣A-Za-z0-9]+?)(?:\\s*등)?(?:을|를)?\\s*(?:꼭\\s*)?지참");
    // '신분증 지참 시 할인'처럼 안 가져가도 되는 혜택 조건은 건너뛴다. 입장 조건은 준비물로 본다.
    private static final Pattern BENEFIT = Pattern.compile("할인|무료|증정|혜택|우대");

    public static List<ChecklistDraft> plan(String overview, WalkingLevel walkingLevel,
                                            IndoorOutdoor indoorOutdoor, int durationMaxMinutes) {
        Map<String, ChecklistDraft> drafts = new LinkedHashMap<>();
        for (String item : officialItems(overview)) {
            putIfRoom(drafts, ChecklistSource.ORIGIN, new Essential(item, OFFICIAL_DESCRIPTION));
        }
        if (walkingLevel == WalkingLevel.HIGH) {
            putIfRoom(drafts, ChecklistSource.KIRI, COMFORTABLE_SHOES);
        }
        if (indoorOutdoor != IndoorOutdoor.INDOOR && indoorOutdoor != null
                && durationMaxMinutes >= LONG_OUTDOOR_MINUTES) {
            putIfRoom(drafts, ChecklistSource.KIRI, WATER);
        }
        return List.copyOf(drafts.values());
    }

    static List<String> officialItems(String overview) {
        if (overview == null || overview.isBlank()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String sentence : SENTENCE_BREAK.split(overview)) {
            Matcher labeled = LABELED.matcher(sentence);
            if (labeled.find()) {
                SourceTextParser.splitItems(labeled.group(1)).forEach(item -> addIfValid(items, item));
                continue;
            }
            if (BENEFIT.matcher(sentence).find()) {
                continue;
            }
            Matcher bring = BRING.matcher(sentence);
            while (bring.find()) {
                addIfValid(items, bring.group(1));
            }
        }
        return items;
    }

    private static void addIfValid(List<String> items, String item) {
        String trimmed = item.trim();
        if (trimmed.length() >= 2 && trimmed.length() <= MAX_ITEM_LENGTH && !items.contains(trimmed)) {
            items.add(trimmed);
        }
    }

    private static void putIfRoom(Map<String, ChecklistDraft> drafts, ChecklistSource source, Essential essential) {
        if (drafts.size() < MAX_ESSENTIALS && !drafts.containsKey(essential.title())) {
            drafts.put(essential.title(), new ChecklistDraft(ItemType.PREPARE, source, essential.title(),
                    essential.description(), drafts.size() + 1));
        }
    }

    private record Essential(String title, String description) {
    }
}
