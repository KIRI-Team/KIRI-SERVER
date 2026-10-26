package com.kiri.server.domain.external.service;

import com.kiri.server.domain.external.client.tourapi.TourApiClient;
import com.kiri.server.domain.external.client.tourapi.TourApiProperties;
import com.kiri.server.domain.external.client.tourapi.dto.TourApiResDTO;
import com.kiri.server.domain.external.dto.SyncResult;
import com.kiri.server.domain.external.dto.SyncWindow;
import com.kiri.server.domain.external.entity.ExternalSyncRetry;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TourApiSyncServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 0, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final ExternalSource SOURCE = ExternalSource.TOUR_API;

    private final JsonMapper jsonMapper = new JsonMapper();

    @Mock
    private TourApiClient tourApiClient;

    @Mock
    private ExternalRecordSyncService externalRecordSyncService;

    @Mock
    private ExternalSyncStateService externalSyncStateService;

    @Mock
    private ExternalSyncRetryService externalSyncRetryService;

    @Mock
    private MissionGenerationService missionGenerationService;

    @Test
    @DisplayName("인증키가 없으면 API·수집 상태를 건드리지 않는다.")
    void sync_withoutServiceKey() {
        SyncResult result = service("", 240, 900).sync();

        assertThat(result.completed()).isFalse();
        verifyNoInteractions(tourApiClient, externalSyncStateService, externalRecordSyncService);
    }

    @Test
    @DisplayName("다른 수집이 실행 중이면 건너뛰고 잠금도 풀지 않는다.")
    void sync_locked() {
        given(externalSyncStateService.tryLock(SOURCE, NOW)).willReturn(false);

        SyncResult result = service("key", 240, 900).sync();

        assertThat(result.completed()).isFalse();
        verify(externalSyncStateService, never()).begin(any(), any());
        verify(externalSyncStateService, never()).unlock(any());
        verifyNoInteractions(tourApiClient);
    }

    @Test
    @DisplayName("전체 수집은 페이지마다 위치를 저장하고, 끝까지 돌면 구간 종료일을 성공 날짜로 남긴다.")
    void sync_fullScan() {
        givenWindow(new SyncWindow(true, null, TODAY, 1, false, 0));
        given(tourApiClient.fetchSyncList(1, null)).willReturn(page(3, item("1"), item("2")));
        given(tourApiClient.fetchSyncList(2, null)).willReturn(page(3, item("3")));
        given(externalRecordSyncService.decide(any()))
                .willReturn(SyncDecision.FETCH, SyncDecision.UNCHANGED, SyncDecision.HIDE);
        givenDetails("1");

        SyncResult result = service("key", 240, 900).sync();

        assertThat(result.completed()).isTrue();
        assertThat(result.calls()).isEqualTo(5);
        verify(missionGenerationService).publishPendingMissions(TODAY, NOW);
        verify(externalSyncStateService).moveTo(SOURCE, null, 1);
        verify(externalSyncStateService).moveTo(SOURCE, null, 2);
        verify(externalSyncStateService).complete(SOURCE, TODAY, NOW);
        verify(externalSyncStateService).addCalls(SOURCE, TODAY, 5);
        verify(externalSyncStateService).unlock(SOURCE);
    }

    @Test
    @DisplayName("증분 수집은 고정된 구간의 날짜별로 수정분을 조회한다.")
    void sync_incremental() {
        givenWindow(new SyncWindow(false, TODAY.minusDays(1), TODAY, 1, false, 0));
        given(tourApiClient.fetchSyncList(1, "20261015")).willReturn(page(0));
        given(tourApiClient.fetchSyncList(1, "20261016")).willReturn(page(0));

        SyncResult result = service("key", 240, 900).sync();

        assertThat(result.completed()).isTrue();
        verify(externalSyncStateService).complete(SOURCE, TODAY, NOW);
    }

    @Test
    @DisplayName("중단된 구간은 저장된 날짜·페이지부터 이어서 수집한다.")
    void sync_resume() {
        givenWindow(new SyncWindow(false, TODAY.minusDays(1), TODAY, 3, true, 0));
        given(tourApiClient.fetchSyncList(3, "20261015")).willReturn(page(0));
        given(tourApiClient.fetchSyncList(1, "20261016")).willReturn(page(0));

        service("key", 240, 900).sync();

        verify(tourApiClient, never()).fetchSyncList(eq(1), eq("20261015"));
        verify(externalSyncStateService).moveTo(SOURCE, TODAY.minusDays(1), 3);
    }

    @Test
    @DisplayName("실행당 호출 예산을 다 쓰면 실패가 아닌 일시 중단으로 기록하고 성공 날짜는 그대로 둔다.")
    void sync_runBudget() {
        givenWindow(new SyncWindow(true, null, TODAY, 1, false, 0));
        given(tourApiClient.fetchSyncList(1, null)).willReturn(page(1, item("1")));
        given(externalRecordSyncService.decide(any())).willReturn(SyncDecision.FETCH);

        SyncResult result = service("key", 3, 900).sync();

        assertThat(result.completed()).isFalse();
        verify(externalSyncStateService).pause(eq(SOURCE), eq(TourApiSyncService.PAUSE_RUN_BUDGET), anyString());
        verify(externalSyncStateService, never()).complete(any(), any(), any());
        verify(externalSyncStateService).addCalls(SOURCE, TODAY, 1);
    }

    @Test
    @DisplayName("오늘 쓴 호출이 일일 한도에 가까우면 남은 만큼만 쓰고 DAILY_LIMIT로 일시 중단한다.")
    void sync_dailyLimit() {
        givenWindow(new SyncWindow(true, null, TODAY, 1, false, 899));
        given(tourApiClient.fetchSyncList(1, null)).willReturn(page(1, item("1")));
        given(externalRecordSyncService.decide(any())).willReturn(SyncDecision.FETCH);

        service("key", 240, 900).sync();

        verify(externalSyncStateService).pause(eq(SOURCE), eq(TourApiSyncService.PAUSE_DAILY_LIMIT), anyString());
        verify(tourApiClient, never()).fetchDetailCommon(anyString());
    }

    @Test
    @DisplayName("API 호출 한도 초과는 원인 코드를 남기고 실패로 기록한다.")
    void sync_limitExceeded() {
        givenWindow(new SyncWindow(true, null, TODAY, 1, false, 0));
        given(tourApiClient.fetchSyncList(anyInt(), isNull()))
                .willThrow(new ExternalException(ExternalErrorCode.LIMIT_EXCEEDED));

        SyncResult result = service("key", 240, 900).sync();

        assertThat(result.completed()).isFalse();
        verify(externalSyncStateService).fail(eq(SOURCE), eq("EXTERNAL429_1"), anyString());
        verify(externalSyncStateService).unlock(SOURCE);
    }

    @Test
    @DisplayName("한 건이 실패하면 재시도 목록에 넣고 나머지는 계속 처리한다.")
    void sync_itemFailure() {
        givenWindow(new SyncWindow(true, null, TODAY, 1, false, 0));
        given(tourApiClient.fetchSyncList(1, null)).willReturn(page(2, item("1"), item("2")));
        given(externalRecordSyncService.decide(any())).willReturn(SyncDecision.FETCH);
        given(tourApiClient.fetchDetailCommon("1")).willThrow(new ExternalException(ExternalErrorCode.ERROR_RESPONSE));
        givenDetails("2");

        SyncResult result = service("key", 240, 900).sync();

        assertThat(result.completed()).isTrue();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.processed()).isEqualTo(1);
        verify(externalSyncRetryService).recordFailure(eq(SOURCE), eq("1"), contains("\"contentid\":\"1\""), anyString());
    }

    @Test
    @DisplayName("이전에 실패한 원본을 먼저 재처리하고, 성공하면 재시도 목록에서 지운다.")
    void sync_retriesFirst() {
        givenWindow(new SyncWindow(false, TODAY, TODAY, 1, false, 0));
        ExternalSyncRetry retry = ExternalSyncRetry.builder()
                .externalSource(SOURCE).externalId("9")
                .rawItem("{\"contentid\":\"9\",\"contenttypeid\":\"15\",\"showflag\":\"1\"}")
                .attemptCount(1)
                .build();
        given(externalSyncRetryService.pending(SOURCE)).willReturn(List.of(retry));
        given(externalRecordSyncService.decide(any())).willReturn(SyncDecision.FETCH);
        givenDetails("9");
        given(tourApiClient.fetchSyncList(1, "20261016")).willReturn(page(0));

        service("key", 240, 900).sync();

        verify(externalRecordSyncService).apply(any(), any(TourApiResDTO.Details.class), eq(NOW));
        verify(externalSyncRetryService).resolve(SOURCE, "9");
    }

    @Test
    @DisplayName("첫 전체 수집이 끝나기 전에는 전체 점검을 하지 않는다.")
    void fullCheck_beforeFirstSync() {
        given(externalSyncStateService.hasCompletedSync(SOURCE)).willReturn(false);

        SyncResult result = service("key", 240, 900).fullCheck();

        assertThat(result.completed()).isFalse();
        verify(externalSyncStateService, never()).tryLock(any(), any());
        verifyNoInteractions(tourApiClient, externalRecordSyncService);
    }

    @Test
    @DisplayName("전체 목록을 끝까지 훑으면 목록에 없는 원본을 비교하고, 수집 진행 위치는 건드리지 않는다.")
    void fullCheck_reconcilesMissing() {
        givenFullCheck(0);
        given(tourApiClient.fetchSyncList(1, null)).willReturn(page(3, item("1"), item("2")));
        given(tourApiClient.fetchSyncList(2, null)).willReturn(page(3, item("3")));
        given(externalRecordSyncService.decide(any())).willReturn(SyncDecision.UNCHANGED);
        given(externalRecordSyncService.reconcileMissing(Set.of("1", "2", "3"))).willReturn(1);

        SyncResult result = service("key", 240, 900).fullCheck();

        assertThat(result.completed()).isTrue();
        assertThat(result.missing()).isEqualTo(1);
        verify(externalSyncStateService, never()).moveTo(any(), any(), anyInt());
        verify(externalSyncStateService).completeFullCheck(SOURCE, NOW);
        verify(externalSyncStateService).addCalls(SOURCE, TODAY, 2);
        verify(externalSyncStateService).unlock(SOURCE);
    }

    @Test
    @DisplayName("전체 점검이 중간에 멈추면 목록 비교를 하지 않는다.")
    void fullCheck_stoppedWithoutReconcile() {
        givenFullCheck(0);
        given(tourApiClient.fetchSyncList(1, null)).willReturn(page(3, item("1"), item("2")));
        given(externalRecordSyncService.decide(any())).willReturn(SyncDecision.FETCH);

        SyncResult result = service("key", 3, 900).fullCheck();

        assertThat(result.completed()).isFalse();
        verify(externalRecordSyncService, never()).reconcileMissing(any());
        verify(externalSyncStateService, never()).completeFullCheck(any(), any());
        verify(externalSyncStateService).unlock(SOURCE);
    }

    private void givenFullCheck(int callsUsedToday) {
        given(externalSyncStateService.hasCompletedSync(SOURCE)).willReturn(true);
        given(externalSyncStateService.tryLock(SOURCE, NOW)).willReturn(true);
        given(externalSyncStateService.callsUsedOn(SOURCE, TODAY)).willReturn(callsUsedToday);
    }

    private void givenWindow(SyncWindow window) {
        given(externalSyncStateService.tryLock(SOURCE, NOW)).willReturn(true);
        given(externalSyncStateService.begin(SOURCE, TODAY)).willReturn(window);
    }

    private TourApiSyncService service(String serviceKey, int maxCallsPerRun, int dailyCallLimit) {
        TourApiProperties properties = new TourApiProperties(
                "https://apis.data.go.kr/B551011/KorService2", serviceKey, "KIRI", 2, "11",
                Duration.ofSeconds(3), Duration.ofSeconds(10), maxCallsPerRun, dailyCallLimit);
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        return new TourApiSyncService(tourApiClient, properties, externalRecordSyncService, externalSyncStateService,
                externalSyncRetryService, missionGenerationService, jsonMapper, clock);
    }

    private void givenDetails(String contentId) {
        given(tourApiClient.fetchDetailCommon(contentId)).willReturn(page(1, json("{\"overview\":\"개요\"}")));
        given(tourApiClient.fetchDetailIntro(contentId, "15")).willReturn(page(1, json("{}")));
        given(tourApiClient.fetchDetailInfo(contentId, "15")).willReturn(page(0));
    }

    private TourApiResDTO.Page page(int totalCount, JsonNode... items) {
        return TourApiResDTO.Page.builder()
                .pageNo(1)
                .numOfRows(items.length)
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
