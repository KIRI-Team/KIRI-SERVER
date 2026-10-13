package com.kiri.server.domain.external.service;

import com.kiri.server.domain.external.client.tourapi.TourApiClient;
import com.kiri.server.domain.external.client.tourapi.TourApiProperties;
import com.kiri.server.domain.external.client.tourapi.dto.TourApiResDTO;
import com.kiri.server.domain.external.converter.TourApiConverter;
import com.kiri.server.domain.external.dto.SyncResult;
import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.exception.ExternalException;
import com.kiri.server.domain.external.exception.code.ExternalErrorCode;
import com.kiri.server.domain.mission.service.MissionGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TourApiSyncService {

    private static final ExternalSource SOURCE = ExternalSource.TOUR_API;
    private static final DateTimeFormatter CURSOR_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int MAX_INCREMENTAL_DAYS = 30;
    private static final int LIST_CALLS = 1;
    private static final int DETAIL_CALLS = 3;
    private static final Set<ExternalErrorCode> FATAL_ERRORS = Set.of(
            ExternalErrorCode.SERVICE_KEY_MISSING, ExternalErrorCode.SERVICE_KEY_INVALID, ExternalErrorCode.LIMIT_EXCEEDED);

    private final TourApiClient tourApiClient;
    private final TourApiProperties properties;
    private final ExternalRecordSyncService externalRecordSyncService;
    private final ExternalSyncStateService externalSyncStateService;
    private final MissionGenerationService missionGenerationService;
    private final Clock clock;

    public SyncResult sync() {
        LocalDateTime startedAt = LocalDateTime.now(clock);
        LocalDate today = startedAt.toLocalDate();
        Run run = new Run(properties.maxCallsPerRun(), startedAt);
        run.endedMissions = missionGenerationService.endExpiredMissions(today, startedAt);

        if (!properties.hasServiceKey()) {
            log.warn("[TourAPI] 인증키가 없어 수집을 건너뜁니다.");
            return run.toResult(false, "인증키 없음");
        }

        try {
            for (String date : targetDates(externalSyncStateService.getCursor(SOURCE), today)) {
                syncDate(date, run);
            }
        } catch (BudgetExhaustedException e) {
            return fail(run, "호출 한도(" + properties.maxCallsPerRun() + "회) 소진으로 중단");
        } catch (RuntimeException e) {
            return fail(run, e.getMessage());
        }

        externalSyncStateService.recordSuccess(SOURCE, today.format(CURSOR_FORMAT), startedAt);
        SyncResult result = run.toResult(true, null);
        log.info("[TourAPI] 수집 완료 {}", result);
        return result;
    }

    private SyncResult fail(Run run, String error) {
        externalSyncStateService.recordFailure(SOURCE, error);
        SyncResult result = run.toResult(false, error);
        log.warn("[TourAPI] 수집 중단 {}", result);
        return result;
    }

    private void syncDate(String date, Run run) {
        int pageNo = 1;
        while (true) {
            run.use(LIST_CALLS);
            TourApiResDTO.Page page = tourApiClient.fetchSyncList(pageNo, date);
            for (JsonNode node : page.items()) {
                syncItem(TourApiConverter.toSyncItem(node), run);
            }
            if (page.items().isEmpty() || !page.hasNext()) {
                return;
            }
            pageNo++;
        }
    }

    private void syncItem(TourApiResDTO.SyncItem item, Run run) {
        if (item.contentId() == null) {
            run.skipped++;
            return;
        }
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
        } catch (BudgetExhaustedException e) {
            throw e;
        } catch (ExternalException e) {
            if (FATAL_ERRORS.contains(e.getErrorCode())) {
                throw e;
            }
            run.failed++;
            log.warn("[TourAPI] contentId={} 처리 실패: {}", item.contentId(), e.getMessage());
        } catch (RuntimeException e) {
            run.failed++;
            log.warn("[TourAPI] contentId={} 처리 실패", item.contentId(), e);
        }
    }

    private TourApiResDTO.Details fetchDetails(TourApiResDTO.SyncItem item) {
        JsonNode common = tourApiClient.fetchDetailCommon(item.contentId()).firstItemOrNull();
        JsonNode intro = tourApiClient.fetchDetailIntro(item.contentId(), item.contentTypeId()).firstItemOrNull();
        List<JsonNode> infos = tourApiClient.fetchDetailInfo(item.contentId(), item.contentTypeId()).items();
        return new TourApiResDTO.Details(common, intro, infos);
    }

    static List<String> targetDates(String cursor, LocalDate today) {
        LocalDate from = parseCursor(cursor);
        if (from == null || from.isAfter(today) || from.isBefore(today.minusDays(MAX_INCREMENTAL_DAYS))) {
            return Collections.singletonList(null);
        }
        List<String> dates = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(today); date = date.plusDays(1)) {
            dates.add(date.format(CURSOR_FORMAT));
        }
        return dates;
    }

    private static LocalDate parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(cursor, CURSOR_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static class Run {

        private final int maxCalls;
        private final LocalDateTime startedAt;
        private int calls;
        private int processed;
        private int unchanged;
        private int hidden;
        private int skipped;
        private int failed;
        private int endedMissions;

        private Run(int maxCalls, LocalDateTime startedAt) {
            this.maxCalls = maxCalls;
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
                    .calls(calls)
                    .endedMissions(endedMissions)
                    .error(error)
                    .build();
        }
    }

    private static class BudgetExhaustedException extends RuntimeException {
    }
}
