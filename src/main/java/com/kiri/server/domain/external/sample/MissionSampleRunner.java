package com.kiri.server.domain.external.sample;

import com.kiri.server.domain.external.client.tourapi.TourApiClient;
import com.kiri.server.domain.external.client.tourapi.dto.TourApiResDTO;
import com.kiri.server.domain.external.converter.TourApiConverter;
import com.kiri.server.domain.external.dto.NormalizedContent;
import com.kiri.server.domain.external.enums.ReviewReason;
import com.kiri.server.domain.mission.enums.ChecklistSource;
import com.kiri.server.domain.mission.generator.CategoryClassifier;
import com.kiri.server.domain.mission.generator.MissionDraftFactory;
import com.kiri.server.domain.mission.generator.dto.DraftResult;
import com.kiri.server.domain.mission.generator.dto.MissionDraft;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mission.sample.enabled", havingValue = "true")
public class MissionSampleRunner implements ApplicationRunner {

    private static final int LIST_ROWS = 300;
    private static final int REVIEW_PER_TYPE = 1;
    private static final List<String> TARGET_TYPES = List.of("12", "14", "15", "28", "39", "38", "32");
    private static final Map<String, String> TYPE_NAMES = Map.of(
            "12", "관광지", "14", "문화시설", "15", "행사", "28", "레포츠", "38", "쇼핑", "39", "음식점", "32", "숙박");
    private static final Map<String, Integer> PER_TYPE = Map.of("15", 8, "38", 4, "32", 2);
    private static final int DEFAULT_PER_TYPE = 6;

    private final TourApiClient tourApiClient;
    private final MissionDraftFactory missionDraftFactory;
    private final ConfigurableApplicationContext context;

    @Value("${mission.sample.output:mission-samples.md}")
    private String output;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        List<String> rows = new ArrayList<>();
        int calls = 0;
        for (String type : TARGET_TYPES) {
            List<TourApiResDTO.SyncItem> listed = tourApiClient.fetchSyncList(1, null, type, LIST_ROWS).items().stream()
                    .map(TourApiConverter::toSyncItem)
                    .filter(TourApiResDTO.SyncItem::shown)
                    .toList();
            calls++;
            List<TourApiResDTO.SyncItem> picked = listed.stream()
                    .sorted(Comparator.comparing((TourApiResDTO.SyncItem item) ->
                            !"SH06".equals(item.lclsSystm2())))
                    .limit(PER_TYPE.getOrDefault(type, DEFAULT_PER_TYPE))
                    .toList();
            List<TourApiResDTO.SyncItem> items = new ArrayList<>(picked);
            listed.stream()
                    .filter(item -> !picked.contains(item) && looksIncomplete(item))
                    .limit(REVIEW_PER_TYPE)
                    .forEach(items::add);
            for (TourApiResDTO.SyncItem item : items) {
                DraftResult result;
                if (CategoryClassifier.isIgnored(item.contentTypeId(), item.lclsSystm2(), item.lclsSystm3())) {
                    result = new DraftResult.Ignored(ReviewReason.EXCLUDED_TYPE);
                } else {
                    JsonNode common = tourApiClient.fetchDetailCommon(item.contentId()).firstItemOrNull();
                    JsonNode intro = tourApiClient.fetchDetailIntro(item.contentId(), item.contentTypeId()).firstItemOrNull();
                    List<JsonNode> infos = tourApiClient.fetchDetailInfo(item.contentId(), item.contentTypeId()).items();
                    calls += 3;
                    NormalizedContent content = TourApiConverter.toNormalizedContent(item, common, intro, infos);
                    result = missionDraftFactory.create(content);
                }
                rows.add(row(item, result));
            }
        }

        StringBuilder markdown = new StringBuilder()
                .append("# 미션 자동 생성 샘플\n\n")
                .append("- TourAPI 호출 ").append(calls).append("회\n")
                .append("- DB에 저장하지 않은 결과라 장소 중복(애매한 장소)·미션 중복 판정은 반영되지 않음\n")
                .append("- 검수 사례 확인용으로 좌표 누락·취소 표시가 있는 원본을 유형별 최대 1건 추가함\n\n")
                .append("| 원본명 | contentid | 유형 | 분류코드 | 결과 | 사유 | 카테고리 | 미션 제목 | 필수 활동 |\n")
                .append("|---|---|---|---|---|---|---|---|---|\n");
        rows.forEach(row -> markdown.append(row).append('\n'));
        Path path = Path.of(output);
        Files.writeString(path, markdown.toString(), StandardCharsets.UTF_8);
        log.info("[Sample] {}건 생성, 호출 {}회, 결과 파일: {}", rows.size(), calls, path.toAbsolutePath());
        System.exit(SpringApplication.exit(context, () -> 0));
    }

    private static boolean looksIncomplete(TourApiResDTO.SyncItem item) {
        return item.latitude() == null || item.longitude() == null
                || item.latitude().signum() == 0 || item.longitude().signum() == 0
                || item.title() == null || item.title().isBlank()
                || MissionDraftFactory.isCancelled(item.title());
    }

    private static String row(TourApiResDTO.SyncItem item, DraftResult result) {
        String code = item.lclsSystm3() != null ? item.lclsSystm3() : item.lclsSystm2();
        String base = cell(item.title()) + " | " + item.contentId() + " | " + TYPE_NAMES.get(item.contentTypeId())
                + " | " + cell(code) + " | ";
        return "| " + base + switch (result) {
            case DraftResult.Ready ready -> "공개 | - | " + ready.draft().category() + " | "
                    + cell(ready.draft().title()) + " | " + checklist(ready.draft()) + " |";
            case DraftResult.ReviewRequired review -> "검수 필요 | " + review.reason() + " | - | - | - |";
            case DraftResult.Ignored ignored -> "제외 | " + ignored.reason() + " | - | - | - |";
        };
    }

    private static String checklist(MissionDraft draft) {
        return draft.checklist().stream()
                .map(step -> "[" + (step.source() == ChecklistSource.ORIGIN ? "원본" : "KIRI") + "] " + cell(step.title()))
                .collect(Collectors.joining("<br>"));
    }

    private static String cell(String text) {
        return text == null ? "-" : text.replace("|", "/").replace("\n", " ");
    }
}
