package com.kiri.server.admin.sync.service;

import com.kiri.server.admin.audit.enums.AuditAction;
import com.kiri.server.admin.audit.enums.AuditTargetType;
import com.kiri.server.admin.audit.service.AdminAuditLogger;
import com.kiri.server.admin.common.AdminErrorCode;
import com.kiri.server.admin.common.AdminException;
import com.kiri.server.admin.common.Reasons;
import com.kiri.server.admin.sync.dto.SyncAdminDTO;
import com.kiri.server.domain.external.client.tourapi.TourApiProperties;
import com.kiri.server.domain.external.entity.ExternalSyncRetry;
import com.kiri.server.domain.external.entity.ExternalSyncState;
import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.enums.ProcessStatus;
import com.kiri.server.domain.external.repository.ExternalSyncRetryRepository;
import com.kiri.server.domain.external.service.ExternalSyncRetryService;
import com.kiri.server.domain.external.service.ExternalSyncStateService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminSyncService {

    // 지금 수집기가 있는 출처. 새 수집기를 붙이면 여기에 추가한다.
    private static final Set<ExternalSource> COLLECTORS = Set.of(ExternalSource.TOUR_API);
    private static final int STALE_DAYS = 30;
    private static final int SAMPLE_LIMIT = 20;

    private final ExternalSyncStateService externalSyncStateService;
    private final ExternalSyncRetryRepository externalSyncRetryRepository;
    private final TourApiProperties tourApiProperties;
    private final AdminSyncRunner adminSyncRunner;
    private final AdminAuditLogger adminAuditLogger;
    private final EntityManager em;
    private final Clock clock;

    @Transactional
    public List<SyncAdminDTO.Source> sources() {
        LocalDateTime now = LocalDateTime.now(clock);
        return Arrays.stream(ExternalSource.values()).map(source -> toSource(source, now)).toList();
    }

    @Transactional(readOnly = true)
    public List<SyncAdminDTO.Retry> retries(ExternalSource source) {
        return externalSyncRetryRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .filter(retry -> source == null || retry.getExternalSource() == source)
                .map(retry -> new SyncAdminDTO.Retry(retry.getId(), retry.getExternalSource(), retry.getExternalId(),
                        retry.getAttemptCount(), retry.getAttemptCount() >= ExternalSyncRetryService.MAX_ATTEMPTS,
                        retry.getLastError(), retry.getCreatedAt(), retry.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public SyncAdminDTO.RunAccepted run(Long adminId, ExternalSource source, SyncAdminDTO.RunMode mode) {
        requireCollector(source);
        ExternalSyncState state = externalSyncStateService.getOrCreateState(source);
        if (state.isAdminPaused()) {
            throw new AdminException(AdminErrorCode.INVALID_STATUS);
        }
        if (isRunning(state, LocalDateTime.now(clock))) {
            throw new AdminException(AdminErrorCode.SYNC_RUNNING);
        }
        SyncAdminDTO.RunMode runMode = mode == null ? SyncAdminDTO.RunMode.INCREMENTAL : mode;
        adminAuditLogger.log(adminId, AuditAction.SYNC_RUN, AuditTargetType.SYNC, state.getId(), null, null,
                Map.of("source", source, "mode", runMode));
        adminSyncRunner.runTourApi(runMode);
        return new SyncAdminDTO.RunAccepted(source, runMode);
    }

    @Transactional
    public SyncAdminDTO.Source pause(Long adminId, ExternalSource source, String reason) {
        String validReason = Reasons.require(reason);
        requireCollector(source);
        ExternalSyncState state = externalSyncStateService.getOrCreateState(source);
        state.pauseByAdmin();
        adminAuditLogger.log(adminId, AuditAction.SYNC_PAUSE, AuditTargetType.SYNC, state.getId(), validReason,
                null, Map.of("source", source));
        return toSource(source, LocalDateTime.now(clock));
    }

    @Transactional
    public SyncAdminDTO.Source resume(Long adminId, ExternalSource source) {
        requireCollector(source);
        ExternalSyncState state = externalSyncStateService.getOrCreateState(source);
        state.resumeByAdmin();
        adminAuditLogger.log(adminId, AuditAction.SYNC_RESUME, AuditTargetType.SYNC, state.getId(), null,
                null, Map.of("source", source));
        return toSource(source, LocalDateTime.now(clock));
    }

    // 재시도 횟수를 0으로 돌려 다음 수집 실행에서 다시 처리되게 한다.
    @Transactional
    public void retry(Long adminId, Long retryId) {
        ExternalSyncRetry retry = externalSyncRetryRepository.findById(retryId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.TARGET_NOT_FOUND));
        int before = retry.getAttemptCount();
        retry.resetAttempts();
        adminAuditLogger.log(adminId, AuditAction.RETRY, AuditTargetType.SYNC_RETRY, retryId, null,
                Map.of("attemptCount", before), Map.of("attemptCount", 0));
    }

    @Transactional(readOnly = true)
    public List<SyncAdminDTO.QualityCheck> quality() {
        LocalDateTime staleBefore = LocalDateTime.now(clock).minusDays(STALE_DAYS);
        return List.of(
                missionCheck("INVALID_COORDINATE", "좌표가 국내 범위(위도 33~39, 경도 124~132)를 벗어난 공개 미션",
                        "m.status IN ('ACTIVE', 'CHECK_REQUIRED') AND (p.latitude NOT BETWEEN 33 AND 39 OR p.longitude NOT BETWEEN 124 AND 132)"),
                missionCheck("PERIOD_MISSING", "기간형인데 시작일 또는 종료일이 비어 있는 미션",
                        "m.status IN ('ACTIVE', 'CHECK_REQUIRED') AND m.mission_type = 'PERIOD' AND (m.start_date IS NULL OR m.end_date IS NULL)"),
                missionCheck("IMAGE_MISSING", "이미지가 없는 공개 미션",
                        "m.status = 'ACTIVE' AND (m.image_url IS NULL OR m.image_url = '')"),
                recordCheck("ORIGIN_MISSING", "외부 목록에서 사라진 원본(missing_count > 0)",
                        "r.missing_count > 0", null),
                recordCheck("STALE", "최근 " + STALE_DAYS + "일 동안 갱신되지 않은 원본",
                        "r.process_status <> 'IGNORED' AND r.synced_at < :staleBefore", staleBefore));
    }

    private SyncAdminDTO.QualityCheck missionCheck(String key, String description, String where) {
        String from = " FROM mission m JOIN place p ON p.place_id = m.place_id WHERE " + where;
        long count = ((Number) em.createNativeQuery("SELECT COUNT(*)" + from).getSingleResult()).longValue();
        List<SyncAdminDTO.QualitySample> samples = ((List<?>) em.createNativeQuery(
                        "SELECT m.mission_id, m.title" + from + " ORDER BY m.mission_id DESC LIMIT " + SAMPLE_LIMIT)
                .getResultList()).stream()
                .map(row -> (Object[]) row)
                .map(row -> new SyncAdminDTO.QualitySample(((Number) row[0]).longValue(), (String) row[1]))
                .toList();
        return new SyncAdminDTO.QualityCheck(key, description, count, samples);
    }

    private SyncAdminDTO.QualityCheck recordCheck(String key, String description, String where, LocalDateTime staleBefore) {
        String from = " FROM external_record r WHERE " + where;
        var countQuery = em.createNativeQuery("SELECT COUNT(*)" + from);
        var sampleQuery = em.createNativeQuery("SELECT r.external_record_id, r.external_id" + from
                + " ORDER BY r.external_record_id DESC LIMIT " + SAMPLE_LIMIT);
        if (staleBefore != null) {
            countQuery.setParameter("staleBefore", staleBefore);
            sampleQuery.setParameter("staleBefore", staleBefore);
        }
        long count = ((Number) countQuery.getSingleResult()).longValue();
        List<SyncAdminDTO.QualitySample> samples = ((List<?>) sampleQuery.getResultList()).stream()
                .map(row -> (Object[]) row)
                .map(row -> new SyncAdminDTO.QualitySample(((Number) row[0]).longValue(), (String) row[1]))
                .toList();
        return new SyncAdminDTO.QualityCheck(key, description, count, samples);
    }

    private SyncAdminDTO.Source toSource(ExternalSource source, LocalDateTime now) {
        ExternalSyncState state = externalSyncStateService.getOrCreateState(source);
        LocalDate today = now.toLocalDate();
        long pendingRetries = em.createQuery(
                        "SELECT COUNT(r) FROM ExternalSyncRetry r WHERE r.externalSource = :source", Long.class)
                .setParameter("source", source).getSingleResult();
        return new SyncAdminDTO.Source(source, COLLECTORS.contains(source), state.isAdminPaused(),
                isRunning(state, now), state.getRunningSince(), state.getLastStatus(), state.getLastErrorCode(),
                state.getLastError(), state.getLastSuccessAt(), state.getLastFullCheckAt(), state.getLastCursor(),
                state.getProgressDate(), state.getProgressPage(), state.callsUsedOn(today),
                tourApiProperties.dailyCallLimit(), tourApiProperties.maxCallsPerRun(), pendingRetries,
                statusCounts(source, null), statusCounts(source, now.minusHours(24)));
    }

    private Map<ProcessStatus, Long> statusCounts(ExternalSource source, LocalDateTime syncedSince) {
        String jpql = "SELECT r.processStatus, COUNT(r) FROM ExternalRecord r WHERE r.externalSource = :source"
                + (syncedSince == null ? "" : " AND r.syncedAt >= :since") + " GROUP BY r.processStatus";
        var query = em.createQuery(jpql, Object[].class).setParameter("source", source);
        if (syncedSince != null) {
            query.setParameter("since", syncedSince);
        }
        Map<ProcessStatus, Long> counts = new EnumMap<>(ProcessStatus.class);
        for (ProcessStatus status : ProcessStatus.values()) {
            counts.put(status, 0L);
        }
        query.getResultList().forEach(row -> counts.put((ProcessStatus) row[0], (Long) row[1]));
        return counts;
    }

    private static boolean isRunning(ExternalSyncState state, LocalDateTime now) {
        return state.getRunningSince() != null
                && state.getRunningSince().isAfter(now.minus(ExternalSyncStateService.LOCK_TIMEOUT));
    }

    private static void requireCollector(ExternalSource source) {
        if (!COLLECTORS.contains(source)) {
            throw new AdminException(AdminErrorCode.UNSUPPORTED_SOURCE);
        }
    }
}
