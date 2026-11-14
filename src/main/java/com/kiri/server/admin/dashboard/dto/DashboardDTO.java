package com.kiri.server.admin.dashboard.dto;

import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.enums.SyncStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class DashboardDTO {

    public enum KpiKey {
        WEEKLY_COMPLETED_FAMILIES,
        WEEKLY_COMPLETED_SCHEDULES,
        NEW_CONNECTED_FAMILIES,
        WEEKLY_ACTIVE_FAMILIES,
        SCHEDULE_CONFIRMATION_RATE,
        ACTIVITY_COMPLETION_RATE,
        NEXT_ACTIVITY_RATE,
        RELATIONSHIP_POSITIVE_RATE
    }

    public enum KpiUnit { COUNT, RATE }

    // 정상 0건(OK + value 0), 데이터 미수집, 관찰 기간 미충족, 시스템 오류를 구분한다.
    public enum DataStatus { OK, NOT_COLLECTED, INSUFFICIENT_PERIOD, ERROR }

    public record Kpi(
            KpiKey key,
            String name,
            KpiUnit unit,
            LocalDate weekStart,
            LocalDate weekEnd,
            Double value,
            Double previousValue,
            Long numerator,
            Long denominator,
            Long sampleSize,
            List<TrendPoint> trend,
            String formula,
            DataStatus dataStatus,
            LocalDateTime computedAt
    ) {
    }

    public record TrendPoint(LocalDate weekStart, Double value, DataStatus dataStatus) {
    }

    public record KpiDetail(Kpi kpi, List<DetailRow> rows, List<Breakdown> breakdown) {
    }

    // 분자에 들어간 대상(가족·일정)
    public record DetailRow(String type, Long id, String label, LocalDateTime at) {
    }

    public record Breakdown(String label, long count) {
    }

    public record Ops(
            long reviewQueueCount,
            Long oldestReviewWaitingHours,
            long reviewOnHoldCount,
            long pendingPublishCount,
            List<SyncSource> syncSources,
            long syncRetryCount,
            long picksHiddenByMissionCount,
            long reviewWithoutCompletionCount,
            LocalDateTime computedAt
    ) {
    }

    public record SyncSource(ExternalSource source, SyncStatus lastStatus, String lastErrorCode,
                             LocalDateTime lastSuccessAt, boolean adminPaused) {
    }
}
