package com.kiri.server.domain.mission.service;

import com.kiri.server.domain.external.entity.ExternalRecord;
import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.enums.ProcessStatus;
import com.kiri.server.domain.external.enums.ReviewReason;
import com.kiri.server.domain.mission.config.MissionProperties;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.entity.MissionChecklistItem;
import com.kiri.server.domain.mission.enums.CancelSource;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.ChecklistSource;
import com.kiri.server.domain.mission.enums.ItemType;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.mission.generator.dto.DraftResult;
import com.kiri.server.domain.mission.generator.dto.MissionDraft;
import com.kiri.server.domain.mission.repository.MissionChecklistItemRepository;
import com.kiri.server.domain.mission.repository.MissionRepository;
import com.kiri.server.domain.place.entity.Place;
import com.kiri.server.domain.place.enums.PlaceType;
import com.kiri.server.domain.place.repository.PlaceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MissionGenerationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);
    private static final BigDecimal LATITUDE = new BigDecimal("37.5283169");
    private static final BigDecimal LONGITUDE = new BigDecimal("126.9327568");
    private static final String PLACE_NAME = "여의도한강공원";
    private static final String ADDRESS = "서울특별시 영등포구 여의동로 330";
    private static final String TITLE = "한강 뚜벅뚜벅 축제 방문하기";

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private MissionChecklistItemRepository missionChecklistItemRepository;

    @Test
    @DisplayName("자동 공개가 켜져 있으면 새 원본으로 장소·미션·필수 활동을 만들고 ACTIVE로 공개한다.")
    void apply_newMission() {
        ExternalRecord record = newRecord();
        givenNoPlaces();
        givenSaves();

        service(true).apply(record, new DraftResult.Ready(draft(TODAY, TODAY.plusDays(2))), TODAY);

        Mission mission = savedMission();
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.ACTIVE);
        assertThat(mission.getPlace().getName()).isEqualTo(PLACE_NAME);
        assertThat(savedChecklist())
                .extracting(MissionChecklistItem::getSource, MissionChecklistItem::getTitle)
                .containsExactly(tuple(ChecklistSource.ORIGIN, "잠수교 걷기"), tuple(ChecklistSource.KIRI, "가장 재미있었던 활동 공유하기"));
        assertThat(record.getProcessStatus()).isEqualTo(ProcessStatus.PROCESSED);
        assertThat(record.getMission()).isSameAs(mission);
    }

    @Test
    @DisplayName("자동 공개가 꺼져 있으면 정상 미션도 비공개(CHECK_REQUIRED)로 만들어 둔다.")
    void apply_autoPublishOff() {
        ExternalRecord record = newRecord();
        givenNoPlaces();
        givenSaves();

        service(false).apply(record, new DraftResult.Ready(draft(TODAY, TODAY.plusDays(2))), TODAY);

        assertThat(savedMission().getStatus()).isEqualTo(MissionStatus.CHECK_REQUIRED);
        assertThat(record.getProcessStatus()).isEqualTo(ProcessStatus.PROCESSED);
    }

    @Test
    @DisplayName("정규화한 이름·주소가 같고 100m 안에 있는 장소는 재사용한다.")
    void apply_reusesSamePlace() {
        Place existing = place(" 여의도 한강공원 ", "서울특별시 영등포구 여의동로 330 (여의도동)", new BigDecimal("37.5287000"));
        given(placeRepository.findAllWithin(any(), any(), any(), any())).willReturn(List.of(existing));
        given(missionRepository.save(any(Mission.class))).willAnswer(invocation -> invocation.getArgument(0));

        service(true).apply(newRecord(), new DraftResult.Ready(draft(TODAY, TODAY.plusDays(2))), TODAY);

        verify(placeRepository, never()).save(any());
        assertThat(savedMission().getPlace()).isSameAs(existing);
    }

    @Test
    @DisplayName("근처에 이름은 같은데 주소가 다른 장소가 있으면 병합하지 않고 PLACE_AMBIGUOUS 검수로 보낸다.")
    void apply_ambiguousPlace() {
        ExternalRecord record = newRecord();
        Place other = place(PLACE_NAME, "서울특별시 영등포구 여의서로 100", LATITUDE);
        given(placeRepository.findAllWithin(any(), any(), any(), any())).willReturn(List.of(other));

        service(true).apply(record, new DraftResult.Ready(draft(TODAY, TODAY.plusDays(2))), TODAY);

        assertThat(record.getProcessStatus()).isEqualTo(ProcessStatus.REVIEW_REQUIRED);
        assertThat(record.getReviewReason()).isEqualTo(ReviewReason.PLACE_AMBIGUOUS);
        verify(missionRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 주소라도 이름이 다른 매장·시설은 별도 장소로 만든다.")
    void apply_differentStoreInSameBuilding() {
        Place store = place("여의도한강공원 편의점", ADDRESS, LATITUDE);
        given(placeRepository.findAllWithin(any(), any(), any(), any())).willReturn(List.of(store));
        given(placeRepository.findAllByName(PLACE_NAME)).willReturn(List.of());
        givenSaves();

        service(true).apply(newRecord(), new DraftResult.Ready(draft(TODAY, TODAY.plusDays(2))), TODAY);

        verify(placeRepository).save(any(Place.class));
    }

    @Test
    @DisplayName("처음 들어온 원본이 이미 끝난 행사면 PERIOD_OVER 사유로 제외한다.")
    void apply_periodOver() {
        ExternalRecord record = newRecord();

        service(true).apply(record, new DraftResult.Ready(draft(TODAY.minusDays(5), TODAY.minusDays(1))), TODAY);

        assertThat(record.getProcessStatus()).isEqualTo(ProcessStatus.IGNORED);
        assertThat(record.getReviewReason()).isEqualTo(ReviewReason.PERIOD_OVER);
        verifyNoInteractions(placeRepository, missionRepository, missionChecklistItemRepository);
    }

    @Test
    @DisplayName("제목·장소·기간이 모두 같은 미션이 있으면 명확한 중복으로 제외한다.")
    void apply_clearDuplicate() {
        ExternalRecord record = newRecord();
        givenNoPlaces();
        given(missionRepository.findAllWithPlaceByTitle(TITLE))
                .willReturn(List.of(periodMission(place(PLACE_NAME, ADDRESS, LATITUDE), TODAY, TODAY.plusDays(2))));

        service(true).apply(record, new DraftResult.Ready(draft(TODAY, TODAY.plusDays(2))), TODAY);

        assertThat(record.getProcessStatus()).isEqualTo(ProcessStatus.IGNORED);
        assertThat(record.getReviewReason()).isEqualTo(ReviewReason.DUPLICATE);
        verify(missionRepository, never()).save(any());
    }

    @Test
    @DisplayName("제목이 같고 가까운 곳에서 기간이 겹치기만 하면 중복 의심 검수로 보낸다.")
    void apply_duplicateSuspect() {
        ExternalRecord record = newRecord();
        givenNoPlaces();
        given(missionRepository.findAllWithPlaceByTitle(TITLE))
                .willReturn(List.of(periodMission(place(PLACE_NAME, ADDRESS, LATITUDE), TODAY, TODAY.plusDays(5))));

        service(true).apply(record, new DraftResult.Ready(draft(TODAY, TODAY.plusDays(2))), TODAY);

        assertThat(record.getProcessStatus()).isEqualTo(ProcessStatus.REVIEW_REQUIRED);
        assertThat(record.getReviewReason()).isEqualTo(ReviewReason.DUPLICATE_SUSPECT);
        verify(missionRepository, never()).save(any());
    }

    @Test
    @DisplayName("제목이 같아도 멀리 있거나 기간이 겹치지 않으면 별개 미션으로 만든다.")
    void apply_sameTitleButNotDuplicate() {
        Mission far = periodMission(place(PLACE_NAME, ADDRESS, new BigDecimal("37.5400000")), TODAY, TODAY.plusDays(5));
        Mission lastYear = periodMission(place(PLACE_NAME, ADDRESS, LATITUDE), TODAY.minusYears(1), TODAY.minusYears(1).plusDays(2));
        givenNoPlaces();
        given(missionRepository.findAllWithPlaceByTitle(TITLE)).willReturn(List.of(far, lastYear));
        givenSaves();
        ExternalRecord record = newRecord();

        service(true).apply(record, new DraftResult.Ready(draft(TODAY, TODAY.plusDays(2))), TODAY);

        assertThat(record.getProcessStatus()).isEqualTo(ProcessStatus.PROCESSED);
    }

    @Test
    @DisplayName("기존 미션은 내용과 필수 활동을 갱신하고 확인 필요 상태를 풀어준다.")
    void apply_updatesExistingMission() {
        Place existingPlace = place(PLACE_NAME, ADDRESS, LATITUDE);
        Mission mission = periodMission(existingPlace, TODAY, TODAY);
        mission.requireCheck();
        ExternalRecord record = newRecord();
        record.markProcessed(mission);
        given(placeRepository.findAllWithin(any(), any(), any(), any())).willReturn(List.of(existingPlace));

        service(true).apply(record, new DraftResult.Ready(draft(TODAY, TODAY.plusDays(2))), TODAY);

        assertThat(mission.getEndDate()).isEqualTo(TODAY.plusDays(2));
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.ACTIVE);
        verify(missionRepository, never()).save(any());
        verify(missionChecklistItemRepository).deleteAllByMissionInBulk(mission);
        assertThat(savedChecklist()).hasSize(2);
    }

    @Test
    @DisplayName("운영자가 수정한 필수 활동은 원본이 바뀌어도 다시 만들지 않는다.")
    void apply_keepsEditedChecklist() {
        Place existingPlace = place(PLACE_NAME, ADDRESS, LATITUDE);
        Mission mission = Mission.builder()
                .place(existingPlace).title(TITLE).description("설명")
                .category(Category.POPUP).missionType(MissionType.PERIOD)
                .startDate(TODAY).endDate(TODAY)
                .checklistEdited(true)
                .build();
        ExternalRecord record = newRecord();
        record.markProcessed(mission);
        given(placeRepository.findAllWithin(any(), any(), any(), any())).willReturn(List.of(existingPlace));

        service(true).apply(record, new DraftResult.Ready(draft(TODAY, TODAY.plusDays(2))), TODAY);

        verifyNoInteractions(missionChecklistItemRepository);
        assertThat(mission.getEndDate()).isEqualTo(TODAY.plusDays(2));
    }

    @Test
    @DisplayName("검수가 필요해진 원본은 노출 중인 미션을 CHECK_REQUIRED로 내린다.")
    void apply_reviewRequired() {
        Mission mission = periodMission(place(PLACE_NAME, ADDRESS, LATITUDE), TODAY, TODAY.plusDays(2));
        ExternalRecord record = newRecord();
        record.markProcessed(mission);

        service(true).apply(record, new DraftResult.ReviewRequired(ReviewReason.NO_LOCATION), TODAY);

        assertThat(record.getReviewReason()).isEqualTo(ReviewReason.NO_LOCATION);
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.CHECK_REQUIRED);
    }

    @Test
    @DisplayName("외부 취소 표시가 확인되면 원본은 제외하고 미션은 외부 취소로 기록한다.")
    void apply_externalCancelled() {
        Mission mission = periodMission(place(PLACE_NAME, ADDRESS, LATITUDE), TODAY, TODAY.plusDays(2));
        ExternalRecord record = newRecord();
        record.markProcessed(mission);

        service(true).apply(record, new DraftResult.Ignored(ReviewReason.EXTERNAL_CANCELLED), TODAY);

        assertThat(record.getProcessStatus()).isEqualTo(ProcessStatus.IGNORED);
        assertThat(record.getReviewReason()).isEqualTo(ReviewReason.EXTERNAL_CANCELLED);
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.CANCELLED);
        assertThat(mission.getCancelSource()).isEqualTo(CancelSource.EXTERNAL);
    }

    @Test
    @DisplayName("제외 업종이 된 원본은 제외 사유를 남기고 기존 미션을 CHECK_REQUIRED로 내린다.")
    void apply_excludedType() {
        Mission mission = periodMission(place(PLACE_NAME, ADDRESS, LATITUDE), TODAY, TODAY.plusDays(2));
        ExternalRecord record = newRecord();
        record.markProcessed(mission);

        service(true).apply(record, new DraftResult.Ignored(ReviewReason.EXCLUDED_TYPE), TODAY);

        assertThat(record.getReviewReason()).isEqualTo(ReviewReason.EXCLUDED_TYPE);
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.CHECK_REQUIRED);
    }

    @Test
    @DisplayName("원본이 비공개로 바뀌면 취소로 확정하지 않고 ORIGIN_UNAVAILABLE 검수로 보낸다.")
    void hide() {
        Mission mission = periodMission(place(PLACE_NAME, ADDRESS, LATITUDE), TODAY, TODAY.plusDays(2));
        ExternalRecord record = newRecord();
        record.markProcessed(mission);

        service(true).hide(record);

        assertThat(record.getReviewReason()).isEqualTo(ReviewReason.ORIGIN_UNAVAILABLE);
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.CHECK_REQUIRED);
    }

    @Test
    @DisplayName("공개 대기 미션은 자동 공개가 켜져 있을 때만 일괄 공개한다.")
    void publishPendingMissions() {
        LocalDateTime now = TODAY.atTime(0, 10);
        given(missionRepository.publishPending(TODAY, now)).willReturn(4);

        assertThat(service(false).publishPendingMissions(TODAY, now)).isZero();
        assertThat(service(true).publishPendingMissions(TODAY, now)).isEqualTo(4);
        verify(missionRepository).publishPending(TODAY, now);
    }

    @Test
    @DisplayName("이름은 공백·대소문자, 주소는 공백·괄호 표기를 무시하고 비교한다.")
    void normalize() {
        assertThat(MissionGenerationService.sameName("여의도 한강공원", "여의도한강공원")).isTrue();
        assertThat(MissionGenerationService.sameAddress(
                "서울특별시 영등포구 여의동로 330 (여의도동)", "서울특별시  영등포구 여의동로 330")).isTrue();
        assertThat(MissionGenerationService.sameAddress("여의동로 330", "여의동로 331")).isFalse();
    }

    @Test
    @DisplayName("두 좌표 사이 거리를 미터 단위로 계산한다.")
    void distanceMeters() {
        double meters = MissionGenerationService.distanceMeters(
                LATITUDE, LONGITUDE, new BigDecimal("37.5292169"), LONGITUDE);

        assertThat(meters).isCloseTo(100.0, within(1.0));
    }

    private MissionGenerationService service(boolean autoPublish) {
        return new MissionGenerationService(placeRepository, missionRepository, missionChecklistItemRepository,
                new MissionProperties(autoPublish));
    }

    private void givenNoPlaces() {
        given(placeRepository.findAllWithin(any(), any(), any(), any())).willReturn(List.of());
        given(placeRepository.findAllByName(PLACE_NAME)).willReturn(List.of());
    }

    private void givenSaves() {
        given(placeRepository.save(any(Place.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(missionRepository.save(any(Mission.class))).willAnswer(invocation -> invocation.getArgument(0));
    }

    private Mission savedMission() {
        ArgumentCaptor<Mission> mission = ArgumentCaptor.forClass(Mission.class);
        verify(missionRepository).save(mission.capture());
        return mission.getValue();
    }

    private List<MissionChecklistItem> savedChecklist() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MissionChecklistItem>> items = ArgumentCaptor.forClass(List.class);
        verify(missionChecklistItemRepository).saveAll(items.capture());
        return items.getValue();
    }

    private ExternalRecord newRecord() {
        return ExternalRecord.builder()
                .externalSource(ExternalSource.TOUR_API)
                .externalId("2786391")
                .processStatus(ProcessStatus.REVIEW_REQUIRED)
                .contentHash("a".repeat(64))
                .syncedAt(TODAY.atStartOfDay())
                .build();
    }

    private Place place(String name, String address, BigDecimal latitude) {
        return Place.builder()
                .name(name).placeType(PlaceType.EVENT_VENUE)
                .latitude(latitude).longitude(LONGITUDE)
                .address(address)
                .build();
    }

    private Mission periodMission(Place place, LocalDate startDate, LocalDate endDate) {
        return Mission.builder()
                .place(place).title(TITLE).description("설명")
                .category(Category.POPUP).missionType(MissionType.PERIOD)
                .startDate(startDate).endDate(endDate)
                .build();
    }

    private MissionDraft draft(LocalDate startDate, LocalDate endDate) {
        return MissionDraft.builder()
                .title(TITLE)
                .description("한강을 걸으며 즐기는 축제")
                .category(Category.POPUP)
                .missionType(MissionType.PERIOD)
                .startDate(startDate)
                .endDate(endDate)
                .place(MissionDraft.PlaceDraft.builder()
                        .name(PLACE_NAME)
                        .placeType(PlaceType.EVENT_VENUE)
                        .latitude(LATITUDE)
                        .longitude(LONGITUDE)
                        .address(ADDRESS)
                        .build())
                .checklist(List.of(
                        new MissionDraft.ChecklistDraft(ItemType.STEP, ChecklistSource.ORIGIN, "잠수교 걷기", null, 1),
                        new MissionDraft.ChecklistDraft(ItemType.STEP, ChecklistSource.KIRI, "가장 재미있었던 활동 공유하기", null, 2)))
                .build();
    }
}
