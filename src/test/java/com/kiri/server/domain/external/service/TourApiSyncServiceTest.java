package com.kiri.server.domain.external.service;

import com.kiri.server.domain.external.client.tourapi.TourApiClient;
import com.kiri.server.domain.external.client.tourapi.TourApiProperties;
import com.kiri.server.domain.external.client.tourapi.dto.TourApiResDTO;
import com.kiri.server.domain.external.dto.SyncResult;
import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.enums.SyncDecision;
import com.kiri.server.domain.external.exception.ExternalException;
import com.kiri.server.domain.external.exception.code.ExternalErrorCode;
import com.kiri.server.domain.mission.service.MissionGenerationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TourApiSyncServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 6, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();

    private final JsonMapper jsonMapper = new JsonMapper();

    @Mock
    private TourApiClient tourApiClient;

    @Mock
    private ExternalRecordSyncService externalRecordSyncService;

    @Mock
    private ExternalSyncStateService externalSyncStateService;

    @Mock
    private MissionGenerationService missionGenerationService;

    @Test
    @DisplayName("인증키가 없으면 API를 부르지 않고 기간 지난 미션 종료만 한다.")
    void sync_withoutServiceKey() {
        given(missionGenerationService.endExpiredMissions(TODAY, NOW)).willReturn(2);

        SyncResult result = service("", 240).sync();

        assertThat(result.completed()).isFalse();
        assertThat(result.endedMissions()).isEqualTo(2);
        verifyNoInteractions(tourApiClient, externalRecordSyncService, externalSyncStateService);
    }

    @Test
    @DisplayName("커서가 없으면 전체 목록을 페이지 끝까지 돌고 판단 결과대로 처리한 뒤 오늘 날짜를 커서로 남긴다.")
    void sync_fullScan() {
        given(externalSyncStateService.getCursor(ExternalSource.TOUR_API)).willReturn(null);
        given(tourApiClient.fetchSyncList(1, null)).willReturn(page(1, 3, item("1"), item("2")));
        given(tourApiClient.fetchSyncList(2, null)).willReturn(page(2, 3, item("3")));
        given(externalRecordSyncService.decide(any()))
                .willReturn(SyncDecision.FETCH, SyncDecision.UNCHANGED, SyncDecision.HIDE);
        givenDetails("1");

        SyncResult result = service("key", 240).sync();

        assertThat(result.completed()).isTrue();
        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(result.hidden()).isEqualTo(1);
        assertThat(result.calls()).isEqualTo(5);
        verify(externalRecordSyncService).apply(any(), any(TourApiResDTO.Details.class), eq(NOW));
        verify(externalRecordSyncService).touch(any(), eq(NOW));
        verify(externalRecordSyncService).hide(any(), eq(NOW));
        verify(externalSyncStateService).recordSuccess(ExternalSource.TOUR_API, "20261014", NOW);
    }

    @Test
    @DisplayName("커서가 있으면 커서 날짜부터 오늘까지 날짜별로 수정분만 가져온다.")
    void sync_incremental() {
        given(externalSyncStateService.getCursor(ExternalSource.TOUR_API)).willReturn("20261013");
        given(tourApiClient.fetchSyncList(1, "20261013")).willReturn(page(1, 0));
        given(tourApiClient.fetchSyncList(1, "20261014")).willReturn(page(1, 0));

        SyncResult result = service("key", 240).sync();

        assertThat(result.completed()).isTrue();
        assertThat(result.calls()).isEqualTo(2);
        verify(externalSyncStateService).recordSuccess(ExternalSource.TOUR_API, "20261014", NOW);
    }

    @Test
    @DisplayName("한 번에 쓸 호출 수를 넘기면 중단하고 커서를 옮기지 않는다.")
    void sync_budgetExhausted() {
        given(externalSyncStateService.getCursor(ExternalSource.TOUR_API)).willReturn(null);
        given(tourApiClient.fetchSyncList(1, null)).willReturn(page(1, 1, item("1")));
        given(externalRecordSyncService.decide(any())).willReturn(SyncDecision.FETCH);

        SyncResult result = service("key", 3).sync();

        assertThat(result.completed()).isFalse();
        assertThat(result.calls()).isEqualTo(1);
        verify(tourApiClient, never()).fetchDetailCommon(anyString());
        verify(externalSyncStateService).recordFailure(eq(ExternalSource.TOUR_API), startsWith("호출 한도"));
        verify(externalSyncStateService, never()).recordSuccess(any(), any(), any());
    }

    @Test
    @DisplayName("일일 한도 초과나 인증키 오류는 전체 수집을 멈추고 실패로 기록한다.")
    void sync_fatalError() {
        given(externalSyncStateService.getCursor(ExternalSource.TOUR_API)).willReturn(null);
        given(tourApiClient.fetchSyncList(1, null)).willReturn(page(1, 2, item("1"), item("2")));
        given(externalRecordSyncService.decide(any())).willReturn(SyncDecision.FETCH);
        given(tourApiClient.fetchDetailCommon("1")).willThrow(new ExternalException(ExternalErrorCode.LIMIT_EXCEEDED));

        SyncResult result = service("key", 240).sync();

        assertThat(result.completed()).isFalse();
        verify(tourApiClient, never()).fetchDetailCommon("2");
        verify(externalSyncStateService).recordFailure(eq(ExternalSource.TOUR_API), anyString());
        verify(externalSyncStateService, never()).recordSuccess(any(), any(), any());
    }

    @Test
    @DisplayName("한 건의 처리 실패는 건너뛰고 나머지를 계속 처리한다.")
    void sync_itemFailure() {
        given(externalSyncStateService.getCursor(ExternalSource.TOUR_API)).willReturn(null);
        given(tourApiClient.fetchSyncList(1, null)).willReturn(page(1, 2, item("1"), item("2")));
        given(externalRecordSyncService.decide(any())).willReturn(SyncDecision.FETCH);
        given(tourApiClient.fetchDetailCommon("1")).willThrow(new ExternalException(ExternalErrorCode.ERROR_RESPONSE));
        givenDetails("2");

        SyncResult result = service("key", 240).sync();

        assertThat(result.completed()).isTrue();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.processed()).isEqualTo(1);
        verify(externalSyncStateService).recordSuccess(ExternalSource.TOUR_API, "20261014", NOW);
    }

    @Test
    @DisplayName("목록 조회가 실패하면 실패 사유를 남긴다.")
    void sync_listFailure() {
        given(externalSyncStateService.getCursor(ExternalSource.TOUR_API)).willReturn(null);
        given(tourApiClient.fetchSyncList(anyInt(), isNull()))
                .willThrow(new ExternalException(ExternalErrorCode.CALL_FAILED, "/areaBasedSyncList2 HTTP 503"));

        SyncResult result = service("key", 240).sync();

        assertThat(result.completed()).isFalse();
        assertThat(result.error()).contains("503");
        verify(externalSyncStateService).recordFailure(eq(ExternalSource.TOUR_API), eq(result.error()));
    }

    @Test
    @DisplayName("커서가 없거나 30일보다 오래됐으면 전체, 아니면 커서 날짜부터 오늘까지 조회한다.")
    void targetDates() {
        assertThat(TourApiSyncService.targetDates(null, TODAY)).containsExactly((String) null);
        assertThat(TourApiSyncService.targetDates("잘못된값", TODAY)).containsExactly((String) null);
        assertThat(TourApiSyncService.targetDates("20260901", TODAY)).containsExactly((String) null);
        assertThat(TourApiSyncService.targetDates("20261012", TODAY))
                .containsExactly("20261012", "20261013", "20261014");
    }

    private TourApiSyncService service(String serviceKey, int maxCallsPerRun) {
        TourApiProperties properties = new TourApiProperties(
                "https://apis.data.go.kr/B551011/KorService2", serviceKey, "KIRI", 100, "11",
                Duration.ofSeconds(3), Duration.ofSeconds(10), maxCallsPerRun);
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        return new TourApiSyncService(tourApiClient, properties, externalRecordSyncService,
                externalSyncStateService, missionGenerationService, clock);
    }

    private void givenDetails(String contentId) {
        given(tourApiClient.fetchDetailCommon(contentId)).willReturn(page(1, 1, json("{\"overview\":\"개요\"}")));
        given(tourApiClient.fetchDetailIntro(contentId, "15")).willReturn(page(1, 1, json("{}")));
        given(tourApiClient.fetchDetailInfo(contentId, "15")).willReturn(page(1, 0));
    }

    private TourApiResDTO.Page page(int pageNo, int totalCount, JsonNode... items) {
        return TourApiResDTO.Page.builder()
                .pageNo(pageNo)
                .numOfRows(2)
                .totalCount(totalCount)
                .items(Arrays.asList(items))
                .build();
    }

    private JsonNode item(String contentId) {
        return json("{\"contentid\":\"" + contentId + "\",\"contenttypeid\":\"15\",\"showflag\":\"1\"}");
    }

    private JsonNode json(String value) {
        return jsonMapper.readTree(value);
    }
}
