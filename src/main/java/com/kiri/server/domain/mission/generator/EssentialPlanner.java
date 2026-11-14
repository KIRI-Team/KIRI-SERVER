package com.kiri.server.domain.mission.generator;

import com.kiri.server.domain.external.converter.SourceTextParser;
import com.kiri.server.domain.external.converter.SourceTextParser.MinuteRange;
import com.kiri.server.domain.external.dto.NormalizedContent;
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

// 미션 근거(공식 준비물/활동 키워드/걷기량/실내외/활동시간)으로 맞춤템을 만듦
public class EssentialPlanner {

    public static final int MAX_ESSENTIALS = 4;
    static final int LONG_OUTDOOR_MINUTES = 180;
    private static final int MAX_ITEM_LENGTH = 20;
    private static final String OFFICIAL_DESCRIPTION = "공식 안내에 있는 준비물이에요.";
    private static final String BENEFIT_DESCRIPTION = "챙겨 가면 혜택을 받을 수 있어요.";
    private static final Essential COMFORTABLE_SHOES = new Essential("편한 신발", "많이 걷는 일정이에요.");
    private static final Essential WATER = new Essential("물", "야외에 오래 머무는 일정이에요.");
    private static final Essential SPARE_CLOTHES = new Essential("여벌옷", "물에 젖을 수 있는 활동이에요.");
    private static final Essential TOWEL = new Essential("수건", "물에 젖을 수 있는 활동이에요.");
    private static final Essential PICNIC_MAT = new Essential("돗자리", "잔디밭에서 쉬어 갈 수 있는 곳이에요.");

    private static final Pattern WATER_PLAY = Pattern.compile(
            "물놀이|수영|카약|카누|패들보드|래프팅|수상레저|워터파크|바닥분수|갯벌");
    private static final Pattern PICNIC = Pattern.compile("피크닉|소풍|잔디광장|잔디밭|돗자리");
    private static final Pattern LONG_WALK = Pattern.compile(
            "걷기|걷는|트레킹|트래킹|둘레길|등산|산책로|탐방로|순례길|숲길|도보\\s*(?:여행|투어|코스)");

    private static final Pattern SENTENCE_BREAK = Pattern.compile("\\n|(?<=[.!?])\\s+");
    private static final Pattern LABELED = Pattern.compile("(?:준비물|지참물)\\s*[:：]\\s*(.+)");
    private static final Pattern BRING = Pattern.compile(
            "((?:[가-힣A-Za-z0-9]{2,}\\s)?[가-힣A-Za-z0-9]+?)(?:\\s*등)?(?:을|를)?\\s*(?:꼭\\s*)?지참");
    // 혜택 조건은 따로 모아 혜택 문구로
    private static final Pattern BENEFIT = Pattern.compile("할인|무료|증정|혜택|우대");

    public static List<ChecklistDraft> plan(NormalizedContent content, WalkingLevel walkingLevel,
                                            IndoorOutdoor indoorOutdoor) {
        Map<String, ChecklistDraft> drafts = new LinkedHashMap<>();
        for (String item : officialItems(content.overview())) {
            putIfRoom(drafts, ChecklistSource.ORIGIN, new Essential(item, OFFICIAL_DESCRIPTION));
        }
        String activityText = activityText(content);
        if (WATER_PLAY.matcher(activityText).find()) {
            putIfRoom(drafts, ChecklistSource.KIRI, SPARE_CLOTHES);
            putIfRoom(drafts, ChecklistSource.KIRI, TOWEL);
        }
        if (walkingLevel != WalkingLevel.LOW && LONG_WALK.matcher(activityText).find()) {
            putIfRoom(drafts, ChecklistSource.KIRI, COMFORTABLE_SHOES);
        }
        boolean outdoor = indoorOutdoor != IndoorOutdoor.INDOOR && indoorOutdoor != null;
        MinuteRange duration = SourceTextParser.parseDurationMinutes(content.durationText());
        if (outdoor && duration != null && duration.max() >= LONG_OUTDOOR_MINUTES) {
            putIfRoom(drafts, ChecklistSource.KIRI, WATER);
        }
        if (outdoor && PICNIC.matcher(activityText).find()) {
            putIfRoom(drafts, ChecklistSource.KIRI, PICNIC_MAT);
        }
        for (String item : benefitItems(content.overview())) {
            putIfRoom(drafts, ChecklistSource.ORIGIN, new Essential(item, BENEFIT_DESCRIPTION));
        }
        return List.copyOf(drafts.values());
    }

    private static String activityText(NormalizedContent content) {
        List<String> texts = new ArrayList<>();
        texts.add(content.title());
        texts.add(content.overview());
        texts.addAll(content.programTexts());
        return String.join("\n", texts.stream().filter(text -> text != null).toList());
    }

    static List<String> officialItems(String overview) {
        return bringItems(overview, false);
    }

    static List<String> benefitItems(String overview) {
        return bringItems(overview, true);
    }

    // benefit이 true면 혜택 조건 문장에서, false면 그 외 문장에서 지참 물품 추출
    private static List<String> bringItems(String overview, boolean benefit) {
        if (overview == null || overview.isBlank()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String sentence : SENTENCE_BREAK.split(overview)) {
            Matcher labeled = LABELED.matcher(sentence);
            if (labeled.find()) {
                if (!benefit) {
                    SourceTextParser.splitItems(labeled.group(1)).forEach(item -> addIfValid(items, item));
                }
                continue;
            }
            if (BENEFIT.matcher(sentence).find() != benefit) {
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
