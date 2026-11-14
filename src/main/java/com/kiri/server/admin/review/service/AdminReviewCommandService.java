package com.kiri.server.admin.review.service;

import com.kiri.server.admin.audit.enums.AuditAction;
import com.kiri.server.admin.audit.enums.AuditTargetType;
import com.kiri.server.admin.audit.service.AdminAuditLogger;
import com.kiri.server.admin.auth.repository.AdminRepository;
import com.kiri.server.admin.common.AdminErrorCode;
import com.kiri.server.admin.common.AdminException;
import com.kiri.server.admin.common.AdminValidationException;
import com.kiri.server.admin.common.Reasons;
import com.kiri.server.admin.content.dto.MissionFormDTO;
import com.kiri.server.admin.content.service.MissionFormValidator;
import com.kiri.server.admin.content.service.MissionWriter;
import com.kiri.server.admin.review.dto.ReviewDTO;
import com.kiri.server.admin.review.entity.MissionReviewTask;
import com.kiri.server.admin.review.enums.ReviewTaskStatus;
import com.kiri.server.admin.review.repository.MissionReviewTaskRepository;
import com.kiri.server.domain.external.entity.ExternalRecord;
import com.kiri.server.domain.external.enums.ProcessStatus;
import com.kiri.server.domain.external.enums.ReviewReason;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.MissionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminReviewCommandService {

    private static final EnumSet<ReviewTaskStatus> OPEN_STATUSES = EnumSet.of(ReviewTaskStatus.OPEN, ReviewTaskStatus.ON_HOLD);
    private static final EnumSet<MissionStatus> KEEP_STATUSES =
            EnumSet.of(MissionStatus.ENDED, MissionStatus.CANCELLED, MissionStatus.HIDDEN);
    private static final int MAX_BULK = 100;

    private final EntityManager em;
    private final MissionReviewTaskRepository taskRepository;
    private final AdminRepository adminRepository;
    private final MissionWriter missionWriter;
    private final ReviewSuggestionService suggestionService;
    private final AdminAuditLogger auditLogger;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    // ===== 탭 ① 검증 실패 =====

    @Transactional
    public ReviewDTO.Task assign(Long adminId, Long recordId, Long assigneeId) {
        ExternalRecord record = lockPending(recordId);
        if (assigneeId != null && !adminRepository.existsById(assigneeId)) {
            throw new AdminException(AdminErrorCode.ADMIN_NOT_FOUND);
        }
        MissionReviewTask task = openTask(record);
        Long before = task.getAssigneeAdminId();
        task.assign(assigneeId);
        auditLogger.log(adminId, AuditAction.ASSIGN, AuditTargetType.EXTERNAL_RECORD, recordId, null,
                mapOf("assigneeId", before), mapOf("assigneeId", assigneeId));
        return toTask(task);
    }

    public MissionFormDTO.ValidationResult revalidate(MissionFormDTO.MissionForm form) {
        return form == null
                ? new MissionFormDTO.ValidationResult(false, List.of(new MissionFormDTO.FieldError("form", "입력값이 없습니다.")))
                : MissionFormValidator.validateForPublish(form);
    }

    @Transactional
    public ReviewDTO.ApproveResult approve(Long adminId, Long recordId, ReviewDTO.ApproveRequest request) {
        ExternalRecord record = lockPending(recordId);
        MissionFormDTO.MissionForm form = request == null ? null : request.form();
        MissionFormDTO.ValidationResult validation = revalidate(form);
        if (!validation.valid()) {
            throw new AdminValidationException(validation.errors());
        }
        LocalDate today = LocalDate.now(clock);
        Mission existing = record.getMission();
        Map<String, Object> before = existing == null ? null : snapshot(existing);

        MissionWriter.WriteResult written;
        if (existing == null) {
            MissionFormDTO.MissionForm suggestion = suggestionService.suggest(record)
                    .map(ReviewSuggestionService.Suggestion::form).orElse(null);
            written = missionWriter.createApproved(form, suggestion);
            written.mission().endIfPeriodOver(today);
        } else {
            written = missionWriter.update(existing, form);
            // 종료·취소·숨김 미션은 검수 승인만으로 다시 공개하지 않는다.
            if (!KEEP_STATUSES.contains(existing.getStatus())) {
                existing.publishFromCheck(today);
            }
        }
        Mission mission = written.mission();
        ReviewReason reason = record.getReviewReason();
        record.markProcessed(mission);

        MissionReviewTask task = openTask(record, reason);
        task.approve(adminId, objectMapper.writeValueAsString(form), request.note(), LocalDateTime.now(clock));

        Map<String, Object> after = snapshot(mission);
        after.put("lockedFields", written.changedFields());
        auditLogger.log(adminId, AuditAction.APPROVE, AuditTargetType.EXTERNAL_RECORD, recordId, request.note(), before, after);
        auditLogger.log(adminId, existing == null ? AuditAction.CREATE : AuditAction.UPDATE, AuditTargetType.MISSION,
                mission.getId(), "검수 승인 #" + recordId, before, after);
        return new ReviewDTO.ApproveResult(mission.getId(), mission.getStatus(), AdminReviewQueryService.lockedFields(mission));
    }

    @Transactional
    public ReviewDTO.Task hold(Long adminId, Long recordId, String reason) {
        String validReason = Reasons.require(reason);
        ExternalRecord record = lockPending(recordId);
        MissionReviewTask task = openTask(record);
        task.hold(validReason);
        auditLogger.log(adminId, AuditAction.HOLD, AuditTargetType.EXTERNAL_RECORD, recordId, validReason, null, null);
        return toTask(task);
    }

    // 원본을 제외 처리한다. 재수집해도 IGNORED 원본은 다시 처리하지 않는다.
    @Transactional
    public ReviewDTO.Task reject(Long adminId, Long recordId, String reason) {
        String validReason = Reasons.require(reason);
        ExternalRecord record = lockPending(recordId);
        ReviewReason before = record.getReviewReason();
        MissionReviewTask task = openTask(record, before);
        record.markIgnored(ReviewReason.ADMIN_EXCLUDED);
        Mission mission = record.getMission();
        if (mission != null && (mission.getStatus() == MissionStatus.ACTIVE || mission.getStatus() == MissionStatus.CHECK_REQUIRED)) {
            MissionStatus missionBefore = mission.getStatus();
            mission.hide();
            auditLogger.log(adminId, AuditAction.HIDE, AuditTargetType.MISSION, mission.getId(), validReason,
                    Map.of("status", missionBefore), Map.of("status", mission.getStatus()));
        }
        task.reject(adminId, validReason, LocalDateTime.now(clock));
        auditLogger.log(adminId, AuditAction.REJECT, AuditTargetType.EXTERNAL_RECORD, recordId, validReason,
                mapOf("reviewReason", before), Map.of("processStatus", ProcessStatus.IGNORED));
        return toTask(task);
    }

    // ===== 탭 ② 공개 대기 =====

    @Transactional
    public ReviewDTO.PublishResult publish(Long adminId, Long missionId) {
        Mission mission = lockPendingPublish(missionId);
        mission.publishFromCheck(LocalDate.now(clock));
        auditLogger.log(adminId, AuditAction.PUBLISH, AuditTargetType.MISSION, missionId, null,
                Map.of("status", MissionStatus.CHECK_REQUIRED), Map.of("status", mission.getStatus()));
        return new ReviewDTO.PublishResult(missionId, mission.getStatus());
    }

    @Transactional
    public ReviewDTO.PublishResult hidePending(Long adminId, Long missionId, String reason) {
        String validReason = Reasons.require(reason);
        Mission mission = lockPendingPublish(missionId);
        mission.hide();
        auditLogger.log(adminId, AuditAction.HIDE, AuditTargetType.MISSION, missionId, validReason,
                Map.of("status", MissionStatus.CHECK_REQUIRED), Map.of("status", mission.getStatus()));
        return new ReviewDTO.PublishResult(missionId, mission.getStatus());
    }

    // 하나라도 공개 대기 상태가 아니면 전체를 롤백한다.
    @Transactional
    public List<ReviewDTO.PublishResult> bulk(Long adminId, ReviewDTO.BulkRequest request) {
        if (request == null || request.action() == null || request.missionIds() == null || request.missionIds().isEmpty()
                || request.missionIds().size() > MAX_BULK) {
            throw new AdminException(AdminErrorCode.INVALID_REQUEST);
        }
        List<ReviewDTO.PublishResult> results = new ArrayList<>();
        for (Long missionId : request.missionIds().stream().distinct().toList()) {
            results.add(request.action() == ReviewDTO.BulkAction.PUBLISH
                    ? publish(adminId, missionId)
                    : hidePending(adminId, missionId, request.reason()));
        }
        return results;
    }

    // ===== 공통 =====

    // 동기화와 동시에 처리되지 않도록 원본 행을 잠근다. 이미 처리됐으면 409.
    private ExternalRecord lockPending(Long recordId) {
        ExternalRecord record = em.find(ExternalRecord.class, recordId, LockModeType.PESSIMISTIC_WRITE);
        if (record == null) {
            throw new AdminException(AdminErrorCode.TARGET_NOT_FOUND);
        }
        if (record.getProcessStatus() != ProcessStatus.REVIEW_REQUIRED) {
            throw new AdminException(AdminErrorCode.ALREADY_RESOLVED);
        }
        return record;
    }

    private Mission lockPendingPublish(Long missionId) {
        Mission mission = em.find(Mission.class, missionId, LockModeType.PESSIMISTIC_WRITE);
        if (mission == null) {
            throw new AdminException(AdminErrorCode.TARGET_NOT_FOUND);
        }
        boolean processed = em.createQuery("""
                        SELECT COUNT(r) FROM ExternalRecord r
                         WHERE r.mission = :mission AND r.processStatus = :processed
                        """, Long.class)
                .setParameter("mission", mission).setParameter("processed", ProcessStatus.PROCESSED)
                .getSingleResult() > 0;
        if (mission.getStatus() != MissionStatus.CHECK_REQUIRED || !processed) {
            throw new AdminException(AdminErrorCode.ALREADY_RESOLVED);
        }
        return mission;
    }

    private MissionReviewTask openTask(ExternalRecord record) {
        return openTask(record, record.getReviewReason());
    }

    private MissionReviewTask openTask(ExternalRecord record, ReviewReason reason) {
        return taskRepository.findFirstByExternalRecordIdAndStatusIn(record.getId(), OPEN_STATUSES)
                .orElseGet(() -> taskRepository.save(MissionReviewTask.open(record.getId(), reason)));
    }

    private static ReviewDTO.Task toTask(MissionReviewTask t) {
        return new ReviewDTO.Task(t.getId(), t.getStatus(), t.getReviewReason(), t.getAssigneeAdminId(), t.getNote(),
                t.getResolvedBy(), t.getResolvedAt(), t.getCreatedAt());
    }

    private static Map<String, Object> snapshot(Mission m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("missionId", m.getId());
        map.put("status", m.getStatus());
        map.put("title", m.getTitle());
        map.put("category", m.getCategory());
        map.put("missionType", m.getMissionType());
        map.put("startDate", m.getStartDate());
        map.put("endDate", m.getEndDate());
        map.put("placeId", m.getPlace() == null ? null : m.getPlace().getId());
        return map;
    }

    private static Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }
}
