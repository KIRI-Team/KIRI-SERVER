package com.kiri.server.admin.sync.dto;

import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.enums.ProcessStatus;
import com.kiri.server.domain.external.enums.SyncStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class SyncAdminDTO {

    public enum RunMode { INCREMENTAL, FULL_CHECK }

    public record Source(
            ExternalSource source,
            boolean collectorAvailable,
            boolean adminPaused,
            boolean running,
            LocalDateTime runningSince,
            SyncStatus lastStatus,
            String lastErrorCode,
            String lastError,
            LocalDateTime lastSuccessAt,
            LocalDateTime lastFullCheckAt,
            String lastCursor,
            LocalDate progressDate,
            Integer progressPage,
            int callsToday,
            int dailyCallLimit,
            int maxCallsPerRun,
            long pendingRetryCount,
            Map<ProcessStatus, Long> recordCounts,
            Map<ProcessStatus, Long> syncedLast24h
    ) {
    }

    public record Retry(
            Long retryId,
            ExternalSource source,
            String externalId,
            int attemptCount,
            boolean exhausted,
            String lastError,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ReasonRequest(String reason) {
    }

    public record RunAccepted(ExternalSource source, RunMode mode) {
    }

    public record QualityCheck(String key, String description, long count, List<QualitySample> samples) {
    }

    public record QualitySample(Long id, String title) {
    }
}
