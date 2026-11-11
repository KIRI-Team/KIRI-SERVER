package com.kiri.server.admin.review.service;

import com.kiri.server.admin.content.dto.MissionFormDTO;
import com.kiri.server.domain.external.client.tourapi.dto.TourApiResDTO;
import com.kiri.server.domain.external.converter.TourApiConverter;
import com.kiri.server.domain.external.dto.NormalizedContent;
import com.kiri.server.domain.external.entity.ExternalRecord;
import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.mission.enums.ItemType;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.mission.generator.CategoryClassifier;
import com.kiri.server.domain.mission.generator.ChecklistPlanner;
import com.kiri.server.domain.mission.generator.MissionDraftFactory;
import com.kiri.server.domain.mission.generator.dto.DraftResult;
import com.kiri.server.domain.mission.generator.dto.MissionDraft;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// 외부 원본으로 검수 화면의 입력 초기값과 자동 검증 결과를 만든다.
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewSuggestionService {

    private static final String TYPE_EVENT = "15";

    private final MissionDraftFactory missionDraftFactory;
    private final ObjectMapper objectMapper;

    public record Suggestion(MissionFormDTO.MissionForm form, DraftResult autoResult) {
    }

    public Optional<Suggestion> suggest(ExternalRecord record) {
        if (record.getExternalSource() != ExternalSource.TOUR_API || record.getRawData() == null) {
            return Optional.empty();
        }
        try {
            NormalizedContent content = normalize(record.getRawData());
            DraftResult result = missionDraftFactory.create(content);
            MissionFormDTO.MissionForm form = result instanceof DraftResult.Ready ready
                    ? fromDraft(ready.draft())
                    : fromContent(content);
            return Optional.of(new Suggestion(form, result));
        } catch (RuntimeException e) {
            log.warn("[ReviewSuggestion] 원본 해석 실패: recordId={}, reason={}", record.getId(), e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public JsonNode parse(String rawData) {
        return rawData == null ? null : objectMapper.readTree(rawData);
    }

    private NormalizedContent normalize(String rawData) {
        JsonNode raw = objectMapper.readTree(rawData);
        TourApiResDTO.SyncItem item = TourApiConverter.toSyncItem(raw.path("item"));
        List<JsonNode> infos = new ArrayList<>();
        raw.path("info").forEach(infos::add);
        return TourApiConverter.toNormalizedContent(item, raw.get("common"), raw.get("intro"), infos);
    }

    static MissionFormDTO.MissionForm fromDraft(MissionDraft draft) {
        MissionDraft.PlaceDraft place = draft.place();
        return MissionFormDTO.MissionForm.builder()
                .title(draft.title())
                .description(draft.description())
                .category(draft.category())
                .missionType(draft.missionType())
                .indoorOutdoor(draft.indoorOutdoor())
                .startDate(draft.startDate())
                .endDate(draft.endDate())
                .openingTime(draft.openingTime())
                .closingTime(draft.closingTime())
                .price(draft.price())
                .durationMinMinutes(draft.durationMinMinutes())
                .durationMaxMinutes(draft.durationMaxMinutes())
                .walkingLevel(draft.walkingLevel())
                .imageUrl(draft.imageUrl())
                .imageSource(draft.imageSource())
                .place(MissionFormDTO.PlaceForm.builder()
                        .name(place.name())
                        .placeType(place.placeType())
                        .regionCode(place.regionCode())
                        .latitude(place.latitude())
                        .longitude(place.longitude())
                        .address(place.address())
                        .openingTime(place.openingTime())
                        .closingTime(place.closingTime())
                        .imageUrl(place.imageUrl())
                        .imageSource(place.imageSource())
                        .build())
                .checklist(draft.checklist().stream()
                        .map(item -> new MissionFormDTO.ChecklistForm(item.itemType(), item.title(), item.description()))
                        .toList())
                .build();
    }

    // 자동 생성이 실패한 원본에서 채울 수 있는 값만 채운다.
    static MissionFormDTO.MissionForm fromContent(NormalizedContent content) {
        boolean event = TYPE_EVENT.equals(content.contentTypeId());
        var classification = CategoryClassifier.classify(content);
        List<MissionFormDTO.ChecklistForm> checklist = classification
                .map(c -> ChecklistPlanner.plan(c.category(), content).stream()
                        .map(item -> new MissionFormDTO.ChecklistForm(item.itemType(), item.title(), item.description()))
                        .toList())
                .orElse(List.of());
        return MissionFormDTO.MissionForm.builder()
                .title(content.title())
                .description(content.overview())
                .category(classification.map(c -> c.category()).orElse(null))
                .missionType(event ? MissionType.PERIOD : MissionType.PERMANENT)
                .indoorOutdoor(classification.map(c -> c.indoorOutdoor()).orElse(null))
                .startDate(event ? content.startDate() : null)
                .endDate(event ? content.endDate() : null)
                .imageUrl(content.imageUrl())
                .imageSource(content.imageSource())
                .place(MissionFormDTO.PlaceForm.builder()
                        .name(content.placeName())
                        .placeType(classification.map(c -> c.placeType()).orElse(null))
                        .regionCode(content.regionCode())
                        .latitude(content.latitude())
                        .longitude(content.longitude())
                        .address(content.address())
                        .build())
                .checklist(checklist.isEmpty() ? List.of() : checklist.stream()
                        .filter(item -> item.itemType() == ItemType.PREPARE || item.itemType() == ItemType.STEP)
                        .toList())
                .build();
    }
}
