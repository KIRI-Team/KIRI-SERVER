package com.kiri.server.domain.mission.service;

import com.kiri.server.domain.external.entity.ExternalRecord;
import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.enums.ProcessStatus;
import com.kiri.server.domain.external.enums.ReviewReason;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.entity.MissionChecklistItem;
import com.kiri.server.domain.mission.enums.Category;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MissionGenerationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private static final BigDecimal LATITUDE = new BigDecimal("37.5283169");
    private static final BigDecimal LONGITUDE = new BigDecimal("126.9327568");

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private MissionChecklistItemRepository missionChecklistItemRepository;

    @InjectMocks
    private MissionGenerationService missionGenerationService;

    @Test
    @DisplayName("새 원본은 장소와 미션, 필수 활동을 만들고 PROCESSED로 연결한다.")
    void apply_newMission() {
        ExternalRecord record = newRecord();
        given(placeRepository.findAllByName("여의도한강공원")).willReturn(List.of());
        given(placeRepository.save(any(Place.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(missionRepository.save(any(Mission.class))).willAnswer(invocation -> invocation.getArgument(0));

        missionGenerationService.apply(record, new DraftResult.Ready(draft(TODAY.plusDays(2))), TODAY);

        ArgumentCaptor<Mission> mission = ArgumentCaptor.forClass(Mission.class);
        verify(missionRepository).save(mission.capture());
        assertThat(mission.getValue().getTitle()).isEqualTo("한강 뚜벅뚜벅 축제 방문하기");
        assertThat(mission.getValue().getPlace().getName()).isEqualTo("여의도한강공원");
        assertThat(mission.getValue().getStatus()).isEqualTo(MissionStatus.ACTIVE);

        List<MissionChecklistItem> items = captureChecklist();
        assertThat(items).extracting(MissionChecklistItem::getTitle).containsExactly("잠수교 걷기", "버스킹 공연");
        assertThat(items).allSatisfy(item -> assertThat(item.getMission()).isSameAs(mission.getValue()));

        assertThat(record.getProcessStatus()).isEqualTo(ProcessStatus.PROCESSED);
        assertThat(record.getMission()).isSameAs(mission.getValue());
        assertThat(record.getPlace()).isSameAs(mission.getValue().getPlace());
    }

    @Test
    @DisplayName("이름이 같고 100m 안에 있는 장소는 새로 만들지 않고 재사용한다.")
    void apply_reusesNearbyPlace() {
        Place existing = place(new BigDecimal("37.5287000"), LONGITUDE);
        given(placeRepository.findAllByName("여의도한강공원")).willReturn(List.of(existing));
        given(missionRepository.save(any(Mission.class))).willAnswer(invocation -> invocation.getArgument(0));

        missionGenerationService.apply(newRecord(), new DraftResult.Ready(draft(TODAY.plusDays(2))), TODAY);

        verify(placeRepository, never()).save(any());
        ArgumentCaptor<Mission> mission = ArgumentCaptor.forClass(Mission.class);
        verify(missionRepository).save(mission.capture());
        assertThat(mission.getValue().getPlace()).isSameAs(existing);
    }

    @Test
    @DisplayName("이름이 같아도 100m보다 멀면 다른 장소로 새로 만든다.")
    void apply_farPlaceIsNew() {
        Place far = place(new BigDecimal("37.5400000"), LONGITUDE);
        given(placeRepository.findAllByName("여의도한강공원")).willReturn(List.of(far));
        given(placeRepository.save(any(Place.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(missionRepository.save(any(Mission.class))).willAnswer(invocation -> invocation.getArgument(0));

        missionGenerationService.apply(newRecord(), new DraftResult.Ready(draft(TODAY.plusDays(2))), TODAY);

        verify(placeRepository).save(any(Place.class));
    }

    @Test
    @DisplayName("처음 들어온 원본이 이미 끝난 행사면 미션을 만들지 않고 IGNORED로 둔다.")
    void apply_newButPeriodOver() {
        ExternalRecord record = newRecord();

        missionGenerationService.apply(record, new DraftResult.Ready(draft(TODAY.minusDays(1))), TODAY);

        assertThat(record.getProcessStatus()).isEqualTo(ProcessStatus.IGNORED);
        verifyNoInteractions(placeRepository, missionRepository, missionChecklistItemRepository);
    }

    @Test
    @DisplayName("제목이 같고 가까운 곳에서 기간이 겹치는 미션이 이미 있으면 만들지 않고 중복 의심 검수로 보낸다.")
    void apply_duplicateSuspect() {
        ExternalRecord record = newRecord();
        Mission existing = periodMission(place(LATITUDE, LONGITUDE), TODAY, TODAY.plusDays(5));
        given(missionRepository.findAllWithPlaceByTitle("한강 뚜벅뚜벅 축제 방문하기")).willReturn(List.of(existing));

        missionGenerationService.apply(record, new DraftResult.Ready(draft(TODAY.plusDays(2))), TODAY);

        assertThat(record.getProcessStatus()).isEqualTo(ProcessStatus.REVIEW_REQUIRED);
        assertThat(record.getReviewReason()).isEqualTo(ReviewReason.DUPLICATE_SUSPECT);
        assertThat(record.getMission()).isNull();
        verify(missionRepository, never()).save(any());
        verifyNoInteractions(placeRepository, missionChecklistItemRepository);
    }

    @Test
    @DisplayName("제목이 같아도 멀리 있거나 기간이 겹치지 않으면 별개 미션으로 만든다.")
    void apply_sameTitleButNotDuplicate() {
        Mission far = periodMission(place(new BigDecimal("37.5400000"), LONGITUDE), TODAY, TODAY.plusDays(5));
        Mission lastYear = periodMission(place(LATITUDE, LONGITUDE), TODAY.minusYears(1), TODAY.minusYears(1).plusDays(2));
        given(missionRepository.findAllWithPlaceByTitle("한강 뚜벅뚜벅 축제 방문하기")).willReturn(List.of(far, lastYear));
        given(placeRepository.findAllByName("여의도한강공원")).willReturn(List.of());
        given(placeRepository.save(any(Place.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(missionRepository.save(any(Mission.class))).willAnswer(invocation -> invocation.getArgument(0));
        ExternalRecord record = newRecord();

        missionGenerationService.apply(record, new DraftResult.Ready(draft(TODAY.plusDays(2))), TODAY);

        assertThat(record.getProcessStatus()).isEqualTo(ProcessStatus.PROCESSED);
        verify(missionRepository).save(any(Mission.class));
    }

    @Test
    @DisplayName("기존 미션은 내용을 갱신하고 필수 활동을 다시 만들며 확인 필요 상태를 풀어준다.")
    void apply_updatesExistingMission() {
        Place existingPlace = place(LATITUDE, LONGITUDE);
        Mission mission = Mission.builder()
                .place(existingPlace).title("옛 제목").description("옛 설명")
                .category(Category.POPUP).missionType(MissionType.PERIOD)
                .startDate(TODAY).endDate(TODAY)
                .status(MissionStatus.CHECK_REQUIRED)
                .build();
        ExternalRecord record = newRecord();
        record.markProcessed(mission);
        given(placeRepository.findAllByName("여의도한강공원")).willReturn(List.of(existingPlace));

        missionGenerationService.apply(record, new DraftResult.Ready(draft(TODAY.plusDays(2))), TODAY);

        assertThat(mission.getTitle()).isEqualTo("한강 뚜벅뚜벅 축제 방문하기");
        assertThat(mission.getEndDate()).isEqualTo(TODAY.plusDays(2));
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.ACTIVE);
        verify(missionRepository, never()).save(any());
        verify(missionChecklistItemRepository).deleteAllByMissionInBulk(mission);
        assertThat(captureChecklist()).hasSize(2);
        assertThat(record.getProcessStatus()).isEqualTo(ProcessStatus.PROCESSED);
    }

    @Test
    @DisplayName("검수가 필요해진 원본은 이미 노출 중인 미션을 CHECK_REQUIRED로 내린다.")
    void apply_reviewRequiredHidesMission() {
        Mission mission = activeMission();
        ExternalRecord record = newRecord();
        record.markProcessed(mission);

        missionGenerationService.apply(record, new DraftResult.ReviewRequired(ReviewReason.NO_LOCATION), TODAY);

        assertThat(record.getProcessStatus()).isEqualTo(ProcessStatus.REVIEW_REQUIRED);
        assertThat(record.getReviewReason()).isEqualTo(ReviewReason.NO_LOCATION);
        assertThat(record.getMission()).isSameAs(mission);
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.CHECK_REQUIRED);
    }

    @Test
    @DisplayName("미션 대상이 아니게 된 원본은 IGNORED로 두고 기존 미션을 CHECK_REQUIRED로 내린다.")
    void apply_ignored() {
        Mission mission = activeMission();
        ExternalRecord record = newRecord();
        record.markProcessed(mission);

        missionGenerationService.apply(record, new DraftResult.Ignored(), TODAY);

        assertThat(record.getProcessStatus()).isEqualTo(ProcessStatus.IGNORED);
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.CHECK_REQUIRED);
    }

    @Test
    @DisplayName("원본이 비공개로 바뀌면 연결된 미션을 CHECK_REQUIRED로 내린다.")
    void hide() {
        Mission mission = activeMission();
        ExternalRecord record = newRecord();
        record.markProcessed(mission);

        missionGenerationService.hide(record);

        assertThat(mission.getStatus()).isEqualTo(MissionStatus.CHECK_REQUIRED);
    }

    @Test
    @DisplayName("기간이 지난 미션 종료는 저장소의 일괄 갱신에 맡긴다.")
    void endExpiredMissions() {
        LocalDateTime now = TODAY.atTime(6, 0);
        given(missionRepository.endExpired(TODAY, now)).willReturn(3);

        assertThat(missionGenerationService.endExpiredMissions(TODAY, now)).isEqualTo(3);
    }

    @Test
    @DisplayName("두 좌표 사이 거리를 미터 단위로 계산한다.")
    void distanceMeters() {
        double meters = MissionGenerationService.distanceMeters(
                LATITUDE, LONGITUDE, new BigDecimal("37.5292169"), LONGITUDE);

        assertThat(meters).isCloseTo(100.0, within(1.0));
    }

    private List<MissionChecklistItem> captureChecklist() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MissionChecklistItem>> items = ArgumentCaptor.forClass(List.class);
        verify(missionChecklistItemRepository).saveAll(items.capture());
        return items.getValue();
    }

    private ExternalRecord newRecord() {
        return ExternalRecord.builder()
                .externalSource(ExternalSource.TOUR_API)
                .externalId("2786391")
                .processStatus(ProcessStatus.IGNORED)
                .contentHash("a".repeat(64))
                .syncedAt(TODAY.atStartOfDay())
                .build();
    }

    private Place place(BigDecimal latitude, BigDecimal longitude) {
        return Place.builder()
                .name("여의도한강공원").placeType(PlaceType.EVENT_VENUE)
                .latitude(latitude).longitude(longitude)
                .address("서울특별시 영등포구 여의동로 330")
                .build();
    }

    private Mission periodMission(Place place, LocalDate startDate, LocalDate endDate) {
        return Mission.builder()
                .place(place).title("한강 뚜벅뚜벅 축제 방문하기").description("설명")
                .category(Category.POPUP).missionType(MissionType.PERIOD)
                .startDate(startDate).endDate(endDate)
                .build();
    }

    private Mission activeMission() {
        return Mission.builder()
                .title("한강 뚜벅뚜벅 축제 방문하기").description("설명")
                .category(Category.POPUP).missionType(MissionType.PERIOD)
                .startDate(TODAY).endDate(TODAY.plusDays(2))
                .build();
    }

    private MissionDraft draft(LocalDate endDate) {
        return MissionDraft.builder()
                .title("한강 뚜벅뚜벅 축제 방문하기")
                .description("한강을 걸으며 즐기는 축제")
                .category(Category.POPUP)
                .missionType(MissionType.PERIOD)
                .startDate(endDate.minusDays(2))
                .endDate(endDate)
                .place(MissionDraft.PlaceDraft.builder()
                        .name("여의도한강공원")
                        .placeType(PlaceType.EVENT_VENUE)
                        .latitude(LATITUDE)
                        .longitude(LONGITUDE)
                        .address("서울특별시 영등포구 여의동로 330")
                        .build())
                .checklist(List.of(
                        new MissionDraft.ChecklistDraft(ItemType.STEP, "잠수교 걷기", null, 1),
                        new MissionDraft.ChecklistDraft(ItemType.STEP, "버스킹 공연", null, 2)))
                .build();
    }
}
