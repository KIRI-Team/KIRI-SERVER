package com.kiri.server.domain.external.service;

import com.kiri.server.domain.external.client.tourapi.TourApiClient;
import com.kiri.server.domain.external.client.tourapi.TourApiProperties;
import com.kiri.server.domain.external.client.tourapi.dto.TourApiResDTO;
import com.kiri.server.domain.external.converter.TourApiConverter;
import com.kiri.server.domain.external.dto.SyncResult;
import com.kiri.server.domain.external.dto.SyncWindow;
import com.kiri.server.domain.external.entity.ExternalSyncRetry;
import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.exception.ExternalException;
import com.kiri.server.domain.external.exception.code.ExternalErrorCode;
import com.kiri.server.domain.mission.service.MissionGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TourApiSyncService {

    public static final String PAUSE_RUN_BUDGET = "RUN_BUDGET";
    public static final String PAUSE_DAILY_LIMIT = "DAILY_LIMIT";
    private static final String UNKNOWN_ERROR = "UNKNOWN";
    private static final ExternalSource SOURCE = ExternalSource.TOUR_API;
    private static final int LIST_CALLS = 1;
    private static final int DETAIL_CALLS = 3;
    private static final Set<ExternalErrorCode> FATAL_ERRORS = Set.of(
            ExternalErrorCode.SERVICE_KEY_MISSING, ExternalErrorCode.SERVICE_KEY_INVALID, ExternalErrorCode.LIMIT_EXCEEDED);

    private final TourApiClient tourApiClient;
    private final TourApiProperties properties;
    private final ExternalRecordSyncService externalRecordSyncService;
    private final ExternalSyncStateService externalSyncStateService;
    private final ExternalSyncRetryService externalSyncRetryService;
    private final MissionGenerationService missionGenerationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SyncResult sync() {
        LocalDateTime startedAt = LocalDateTime.now(clock);
        LocalDate today = startedAt.toLocalDate();

        if (!properties.hasServiceKey()) {
            log.warn("[TourAPI] 인증키가 없어 수집을 건너뜁니다.");
            return SyncResult.builder().completed(false).error("인증키 없음").build();
        }
        if (!externalSyncStateService.tryLock(SOURCE, startedAt)) {
            log.warn("[TourAPI] 이미 수집이 실행 중이라 건너뜁니다.");
            return SyncResult.builder().completed(false).error("이미 실행 중").build();
        }

        Run run = null;
        try {
            missionGenerationService.publishPendingMissions(today, startedAt);
            SyncWindow window = externalSyncStateService.begin(SOURCE, today);
            int dailyRemaining = properties.dailyCallLimit() - window.callsUsedToday();
            run = new Run(Math.min(properties.maxCallsPerRun(), dailyRemaining),
                    dailyRemaining < properties.maxCallsPerRun(), startedAt);
            return execute(window, run);
        } finally {
            if (run != null) {
                externalSyncStateService.addCalls(SOURCE, today, run.calls);
            }
            externalSyncStateService.unlock(SOURCE);
        }
    }

    public SyncResult fullCheck() {
        LocalDateTime startedAt = LocalDateTime.now(clock);
        LocalDate today = startedAt.toLocalDate();

        if (!properties.hasServiceKey()) {
            log.warn("[TourAPI] 인증키가 없어 전체 점검을 건너뜁니다.");
            return SyncResult.builder().completed(false).error("인증키 없음").build();
        }
        if (!externalSyncStateService.hasCompletedSync(SOURCE)) {
            log.info("[TourAPI] 첫 전체 수집이 끝나지 않아 전체 점검을 건너뜁니다.");
            return SyncResult.builder().completed(false).error("첫 수집 미완료").build();
        }
        if (!externalSyncStateService.tryLock(SOURCE, startedAt)) {
            log.warn("[TourAPI] 이미 수집이 실행 중이라 전체 점검을 건너뜁니다.");
            return SyncResult.builder().completed(false).error("이미 실행 중").build();
        }

        Run run = null;
        try {
            int dailyRemaining = properties.dailyCallLimit() - externalSyncStateService.callsUsedOn(SOURCE, today);
            run = new Run(Math.min(properties.maxCallsPerRun(), dailyRemaining),
                    dailyRemaining < properties.maxCallsPerRun(), startedAt);
            return executeFullCheck(run);
        } finally {
            if (run != null) {
                externalSyncStateService.addCalls(SOURCE, today, run.calls);
            }
            externalSyncStateService.unlock(SOURCE);
        }
    }

    private SyncResult executeFullCheck(Run run) {
        try {
            syncPages(null, 1, run, false);
        } catch (BudgetExhaustedException e) {
            return finish(run, false, run.limitedByDaily ? PAUSE_DAILY_LIMIT : PAUSE_RUN_BUDGET);
        } catch (RuntimeException e) {
            log.error("[TourAPI] 전체 점검 중 오류", e);
            return finish(run, false, e.getMessage());
        }
        run.missing = externalRecordSyncService.reconcileMissing(run.seen);
        externalSyncStateService.completeFullCheck(SOURCE, run.startedAt);
        return finish(run, true, null);
    }

    private SyncResult execute(SyncWindow window, Run run) {
        try {
            retryFailedItems(run);
            if (window.full()) {
                syncPages(null, window.startPage(), run, true);
            } else {
                for (LocalDate date = window.fromDate(); !date.isAfter(window.endDate()); date = date.plusDays(1)) {
                    syncPages(date, date.equals(window.fromDate()) ? window.startPage() : 1, run, true);
                }
            }
        } catch (BudgetExhaustedException e) {
            String reason = run.limitedByDaily ? PAUSE_DAILY_LIMIT : PAUSE_RUN_BUDGET;
            externalSyncStateService.pause(SOURCE, reason, "호출 " + run.calls + "회 사용 후 일시 중단");
            return finish(run, false, reason);
        } catch (ExternalException e) {
            externalSyncStateService.fail(SOURCE, e.getErrorCode().getCode(), e.getMessage());
            return finish(run, false, e.getMessage());
        } catch (RuntimeException e) {
            externalSyncStateService.fail(SOURCE, UNKNOWN_ERROR, e.getMessage());
            log.error("[TourAPI] 수집 중 알 수 없는 오류", e);
            return finish(run, false, e.getMessage());
        }
        externalSyncStateService.complete(SOURCE, window.endDate(), run.startedAt);
        return finish(run, true, null);
    }

    private SyncResult finish(Run run, boolean completed, String error) {
        SyncResult result = run.toResult(completed, error);
        if (completed) {
            log.info("[TourAPI] 수집 완료 {}", result);
        } else {
            log.warn("[TourAPI] 수집 중단 {}", result);
        }
        return result;
    }

    private void retryFailedItems(Run run) {
        List<ExternalSyncRetry> retries = externalSyncRetryService.pending(SOURCE);
        for (ExternalSyncRetry retry : retries) {
            TourApiResDTO.SyncItem item = TourApiConverter.toSyncItem(objectMapper.readTree(retry.getRawItem()));
            if (syncItem(item, run)) {
                externalSyncRetryService.resolve(SOURCE, retry.getExternalId());
            }
        }
    }

    private void syncPages(LocalDate date, int startPage, Run run, boolean saveProgress) {
        String modifiedDate = date == null ? null : date.format(DateTimeFormatter.BASIC_ISO_DATE);
        int pageNo = startPage;
        while (true) {
            if (saveProgress) {
                externalSyncStateService.moveTo(SOURCE, date, pageNo);
            }
            run.use(LIST_CALLS);
            TourApiResDTO.Page page = tourApiClient.fetchSyncList(pageNo, modifiedDate);
            for (JsonNode node : page.items()) {
                syncItem(TourApiConverter.toSyncItem(node), run);
            }
            if (page.items().isEmpty() || (long) pageNo * properties.numOfRows() >= page.totalCount()) {
                return;
            }
            pageNo++;
        }
    }

    private boolean syncItem(TourApiResDTO.SyncItem item, Run run) {
        if (item.contentId() == null) {
            run.skipped++;
            return true;
        }
        run.seen.add(item.contentId());
        try {
            switch (externalRecordSyncService.decide(item)) {
                case SKIP -> run.skipped++;
                case HIDE -> {
                    externalRecordSyncService.hide(item, run.startedAt);
                    run.hidden++;
                }
                case UNCHANGED -> {
                    externalRecordSyncService.touch(item, run.startedAt);
                    run.unchanged++;
                }
                case FETCH -> {
                    run.use(DETAIL_CALLS);
                    externalRecordSyncService.apply(item, fetchDetails(item), run.startedAt);
                    run.processed++;
                }
            }
            return true;
        } catch (BudgetExhaustedException e) {
            throw e;
        } catch (ExternalException e) {
            if (FATAL_ERRORS.contains(e.getErrorCode())) {
                throw e;
            }
            return failItem(item, run, e.getMessage(), e);
        } catch (RuntimeException e) {
            return failItem(item, run, e.getMessage(), e);
        }
    }

    private boolean failItem(TourApiResDTO.SyncItem item, Run run, String message, RuntimeException cause) {
        run.failed++;
        log.warn("[TourAPI] contentId={} 처리 실패, 다음 실행에서 재시도: {}", item.contentId(), message);
        String raw = item.raw() == null ? "{}" : objectMapper.writeValueAsString(item.raw());
        externalSyncRetryService.recordFailure(SOURCE, item.contentId(), raw,
                cause.getClass().getSimpleName() + ": " + message);
        return false;
    }

    private TourApiResDTO.Details fetchDetails(TourApiResDTO.SyncItem item) {
        JsonNode common = tourApiClient.fetchDetailCommon(item.contentId()).firstItemOrNull();
        JsonNode intro = tourApiClient.fetchDetailIntro(item.contentId(), item.contentTypeId()).firstItemOrNull();
        List<JsonNode> infos = tourApiClient.fetchDetailInfo(item.contentId(), item.contentTypeId()).items();
        return new TourApiResDTO.Details(common, intro, infos);
    }

    private static class Run {

        private final int maxCalls;
        private final boolean limitedByDaily;
        private final LocalDateTime startedAt;
        private int calls;
        private int processed;
        private int unchanged;
        private int hidden;
        private int skipped;
        private int failed;
        private int missing;
        private final Set<String> seen = new HashSet<>();

        private Run(int maxCalls, boolean limitedByDaily, LocalDateTime startedAt) {
            this.maxCalls = Math.max(0, maxCalls);
            this.limitedByDaily = limitedByDaily;
            this.startedAt = startedAt;
        }

        private void use(int count) {
            if (calls + count > maxCalls) {
                throw new BudgetExhaustedException();
            }
            calls += count;
        }

        private SyncResult toResult(boolean completed, String error) {
            return SyncResult.builder()
                    .completed(completed)
                    .processed(processed)
                    .unchanged(unchanged)
                    .hidden(hidden)
                    .skipped(skipped)
                    .failed(failed)
                    .missing(missing)
                    .calls(calls)
                    .error(error)
                    .build();
        }
    }

    private static class BudgetExhaustedException extends RuntimeException {
    }
}
