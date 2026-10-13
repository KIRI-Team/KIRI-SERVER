package com.kiri.server.domain.external.service;

import com.kiri.server.domain.external.client.tourapi.dto.TourApiResDTO;
import com.kiri.server.domain.external.converter.TourApiConverter;
import com.kiri.server.domain.external.entity.ExternalRecord;
import com.kiri.server.domain.external.entity.ExternalRecordHistory;
import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.enums.ProcessStatus;
import com.kiri.server.domain.external.enums.SyncDecision;
import com.kiri.server.domain.external.repository.ExternalRecordHistoryRepository;
import com.kiri.server.domain.external.repository.ExternalRecordRepository;
import com.kiri.server.domain.mission.generator.MissionDraftFactory;
import com.kiri.server.domain.mission.generator.RuleOnlyTitlePolisher;
import com.kiri.server.domain.mission.generator.dto.DraftResult;
import com.kiri.server.domain.mission.service.MissionGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExternalRecordSyncServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 6, 0);
    private static final LocalDateTime MODIFIED_AT = LocalDateTime.of(2026, 10, 10, 15, 30);

    private final JsonMapper jsonMapper = new JsonMapper();

    @Mock
    private ExternalRecordRepository externalRecordRepository;

    @Mock
    private ExternalRecordHistoryRepository externalRecordHistoryRepository;

    @Mock
    private MissionGenerationService missionGenerationService;

    private ExternalRecordSyncService externalRecordSyncService;

    @BeforeEach
    void setUp() {
        externalRecordSyncService = new ExternalRecordSyncService(
                externalRecordRepository, externalRecordHistoryRepository,
                new MissionDraftFactory(new RuleOnlyTitlePolisher()), missionGenerationService, jsonMapper);
    }

    @Test
    @DisplayName("비공개 원본은 저장된 적이 있을 때만 숨김 처리 대상이다.")
    void decide_hidden() {
        TourApiResDTO.SyncItem hidden = syncItem("15", "0", "20261010153000");
        givenRecord(null);

        assertThat(externalRecordSyncService.decide(hidden)).isEqualTo(SyncDecision.SKIP);

        givenRecord(record(MODIFIED_AT));

        assertThat(externalRecordSyncService.decide(hidden)).isEqualTo(SyncDecision.HIDE);
    }

    @Test
    @DisplayName("처음 보는 원본은 미션 대상 유형일 때만 상세를 가져온다.")
    void decide_new() {
        givenRecord(null);

        assertThat(externalRecordSyncService.decide(syncItem("15", "1", "20261010153000"))).isEqualTo(SyncDecision.FETCH);
        assertThat(externalRecordSyncService.decide(syncItem("32", "1", "20261010153000"))).isEqualTo(SyncDecision.SKIP);
    }

    @Test
    @DisplayName("저장된 원본은 수정 시각이 같으면 건너뛰고 다르면 상세를 다시 가져온다.")
    void decide_existing() {
        givenRecord(record(MODIFIED_AT));

        assertThat(externalRecordSyncService.decide(syncItem("15", "1", "20261010153000"))).isEqualTo(SyncDecision.UNCHANGED);
        assertThat(externalRecordSyncService.decide(syncItem("15", "1", "20261012090000"))).isEqualTo(SyncDecision.FETCH);
    }

    @Test
    @DisplayName("새 원본은 목록·상세를 묶어 해시와 함께 저장하고 미션 가공 결과를 반영한다.")
    void apply_new() {
        givenRecord(null);
        given(externalRecordRepository.save(any(ExternalRecord.class))).willAnswer(invocation -> invocation.getArgument(0));

        externalRecordSyncService.apply(syncItem("15", "1", "20261010153000"), details(), NOW);

        ArgumentCaptor<ExternalRecord> saved = ArgumentCaptor.forClass(ExternalRecord.class);
        verify(externalRecordRepository).save(saved.capture());
        ExternalRecord record = saved.getValue();
        assertThat(record.getExternalSource()).isEqualTo(ExternalSource.TOUR_API);
        assertThat(record.getExternalId()).isEqualTo("2786391");
        assertThat(record.getContentHash()).hasSize(64).matches("[0-9a-f]+");
        assertThat(record.getOriginModifiedAt()).isEqualTo(MODIFIED_AT);
        assertThat(record.getSourceUrl()).isEqualTo("https://hangang.seoul.go.kr");
        assertThat(record.getSyncedAt()).isEqualTo(NOW);

        JsonNode raw = jsonMapper.readTree(record.getRawData());
        assertThat(raw.path("item").path("contentid").asString()).isEqualTo("2786391");
        assertThat(raw.path("intro").path("eventstartdate").asString()).isEqualTo("20261010");
        assertThat(raw.path("info").size()).isEqualTo(1);

        ArgumentCaptor<DraftResult> result = ArgumentCaptor.forClass(DraftResult.class);
        verify(missionGenerationService).apply(eq(record), result.capture(), eq(NOW.toLocalDate()));
        assertThat(result.getValue()).isInstanceOf(DraftResult.Ready.class);
        verify(externalRecordHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("내용이 바뀐 원본은 이전 원본을 이력으로 남기고 갱신한 뒤 다시 가공한다.")
    void apply_changed() {
        ExternalRecord record = record(MODIFIED_AT);
        givenRecord(record);

        externalRecordSyncService.apply(syncItem("15", "1", "20261012090000"), details(), NOW);

        ArgumentCaptor<ExternalRecordHistory> history = ArgumentCaptor.forClass(ExternalRecordHistory.class);
        verify(externalRecordHistoryRepository).save(history.capture());
        assertThat(history.getValue().getContentHash()).isEqualTo("a".repeat(64));
        assertThat(history.getValue().getRawData()).isEqualTo("{}");
        assertThat(record.getContentHash()).isNotEqualTo("a".repeat(64));
        assertThat(record.getOriginModifiedAt()).isEqualTo(LocalDateTime.of(2026, 10, 12, 9, 0));
        verify(missionGenerationService).apply(eq(record), any(DraftResult.class), eq(NOW.toLocalDate()));
    }

    @Test
    @DisplayName("수정 시각만 바뀌고 내용이 같으면 이력·재가공 없이 동기화 정보만 갱신한다.")
    void apply_sameContent() {
        givenRecord(null);
        given(externalRecordRepository.save(any(ExternalRecord.class))).willAnswer(invocation -> invocation.getArgument(0));
        TourApiResDTO.SyncItem item = syncItem("15", "1", "20261010153000");
        externalRecordSyncService.apply(item, details(), NOW);
        ArgumentCaptor<ExternalRecord> saved = ArgumentCaptor.forClass(ExternalRecord.class);
        verify(externalRecordRepository).save(saved.capture());
        ExternalRecord record = saved.getValue();
        givenRecord(record);

        externalRecordSyncService.apply(item, details(), NOW.plusHours(6));

        assertThat(record.getSyncedAt()).isEqualTo(NOW.plusHours(6));
        verify(externalRecordHistoryRepository, never()).save(any());
        verify(missionGenerationService).apply(eq(record), any(DraftResult.class), eq(NOW.toLocalDate()));
    }

    @Test
    @DisplayName("비공개로 바뀐 원본은 미션을 내리고 동기화 시각을 갱신한다.")
    void hide() {
        ExternalRecord record = record(MODIFIED_AT);
        givenRecord(record);

        externalRecordSyncService.hide(syncItem("15", "0", "20261012090000"), NOW);

        verify(missionGenerationService).hide(record);
        assertThat(record.getSyncedAt()).isEqualTo(NOW);
    }

    private void givenRecord(ExternalRecord record) {
        given(externalRecordRepository.findByExternalSourceAndExternalId(ExternalSource.TOUR_API, "2786391"))
                .willReturn(Optional.ofNullable(record));
    }

    private ExternalRecord record(LocalDateTime originModifiedAt) {
        return ExternalRecord.builder()
                .externalSource(ExternalSource.TOUR_API)
                .externalId("2786391")
                .processStatus(ProcessStatus.PROCESSED)
                .rawData("{}")
                .contentHash("a".repeat(64))
                .originModifiedAt(originModifiedAt)
                .syncedAt(NOW.minusHours(6))
                .build();
    }

    private TourApiResDTO.SyncItem syncItem(String contentTypeId, String showflag, String modifiedtime) {
        return TourApiConverter.toSyncItem(jsonMapper.readTree("""
                {"contentid":"2786391","contenttypeid":"%s","title":"한강 뚜벅뚜벅 축제",
                 "addr1":"서울특별시 영등포구 여의동로 330",
                 "mapx":"126.9327568","mapy":"37.5283169",
                 "lDongRegnCd":"11","lDongSignguCd":"560","lclsSystm1":"EV","lclsSystm2":"EV01",
                 "showflag":"%s","modifiedtime":"%s"}
                """.formatted(contentTypeId, showflag, modifiedtime)));
    }

    private TourApiResDTO.Details details() {
        JsonNode common = jsonMapper.readTree("""
                {"overview":"한강을 걸으며 즐기는 축제","homepage":"<a href=\\"https://hangang.seoul.go.kr\\">한강</a>"}
                """);
        JsonNode intro = jsonMapper.readTree("""
                {"eventstartdate":"20261010","eventenddate":"20261020","eventplace":"여의도한강공원 일대"}
                """);
        JsonNode info = jsonMapper.readTree("""
                {"infoname":"행사프로그램","infotext":"잠수교 걷기, 버스킹 공연"}
                """);
        return new TourApiResDTO.Details(common, intro, List.of(info));
    }
}
