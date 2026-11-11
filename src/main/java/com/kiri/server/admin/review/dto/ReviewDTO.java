package com.kiri.server.admin.review.dto;

import com.kiri.server.admin.audit.dto.AuditLogDTO;
import com.kiri.server.admin.content.dto.MissionFormDTO;
import com.kiri.server.admin.review.enums.ReviewTaskStatus;
import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.enums.ProcessStatus;
import com.kiri.server.domain.external.enums.ReviewReason;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.LockedField;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.enums.MissionType;
import io.swagger.v3.oas.annotations.Parameter;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ReviewDTO {

    // ===== 탭 ① 검증 실패 =====

    public record QueueCondition(
            @Parameter(description = "검수 사유") ReviewReason reason,
            @Parameter(description = "출처") ExternalSource source,
            @Parameter(description = "지역 코드 앞자리(2자리 시·도, 5자리 시·군·구)") String regionCode,
            @Parameter(description = "연결된 미션의 카테고리") Category category,
            @Parameter(description = "담당자 관리자 ID") Long assigneeId,
            @Parameter(description = "업무 상태. OPEN(미처리) 또는 ON_HOLD(보류)") ReviewTaskStatus taskStatus,
            @Parameter(description = "최소 대기시간(시간)") Integer minWaitingHours,
            @Parameter(description = "원본 제목 부분 일치") String keyword,
            Integer page,
            Integer size
    ) {
    }

    public record QueueItem(
            Long externalRecordId,
            ExternalSource source,
            String externalId,
            String originTitle,
            ReviewReason reason,
            LocalDateTime reviewRequestedAt,
            Long waitingHours,
            Long missionId,
            String missionTitle,
            MissionStatus missionStatus,
            Category category,
            String placeName,
            String regionCode,
            ReviewTaskStatus taskStatus,
            Long assigneeId,
            String assigneeName,
            boolean previouslyApproved
    ) {
    }

    public record ReviewDetail(
            Long externalRecordId,
            ExternalSource source,
            String externalId,
            String sourceUrl,
            ProcessStatus processStatus,
            ReviewReason reason,
            LocalDateTime reviewRequestedAt,
            LocalDateTime syncedAt,
            JsonNode origin,
            JsonNode previousOrigin,
            List<String> changedFields,
            AutoResult autoResult,
            MissionFormDTO.MissionForm suggestion,
            CurrentMission currentMission,
            List<DuplicateCandidate> duplicateCandidates,
            Task task,
            boolean previouslyApproved,
            MissionFormDTO.MissionForm previousOverrides,
            List<Task> taskHistory,
            List<AuditLogDTO.AuditLogItem> auditLogs
    ) {
    }

    // 원본으로 자동 생성을 다시 돌린 결과. READY / REVIEW_REQUIRED / IGNORED
    public record AutoResult(String result, ReviewReason reason) {
    }

    public record CurrentMission(
            Long missionId,
            MissionStatus status,
            List<LockedField> lockedFields,
            MissionFormDTO.MissionForm form
    ) {
    }

    public record DuplicateCandidate(
            Long missionId,
            String title,
            MissionStatus status,
            MissionType missionType,
            LocalDate startDate,
            LocalDate endDate,
            String placeName,
            Double distanceMeters,
            boolean sameTitle
    ) {
    }

    public record Task(
            Long taskId,
            ReviewTaskStatus status,
            ReviewReason reason,
            Long assigneeId,
            String note,
            Long resolvedBy,
            LocalDateTime resolvedAt,
            LocalDateTime createdAt
    ) {
    }

    public record AssignRequest(Long assigneeId) {
    }

    public record ApproveRequest(MissionFormDTO.MissionForm form, String note) {
    }

    public record ReasonRequest(String reason) {
    }

    public record ApproveResult(Long missionId, MissionStatus missionStatus, List<LockedField> lockedFields) {
    }

    // ===== 탭 ② 공개 대기 =====

    public record PendingCondition(
            @Parameter(description = "카테고리") Category category,
            @Parameter(description = "지역 코드 앞자리") String regionCode,
            @Parameter(description = "출처") ExternalSource source,
            @Parameter(description = "미션명 부분 일치") String keyword,
            Integer page,
            Integer size
    ) {
    }

    public record PendingItem(
            Long missionId,
            String title,
            Category category,
            MissionType missionType,
            LocalDate startDate,
            LocalDate endDate,
            String imageUrl,
            String placeName,
            String regionCode,
            ExternalSource source,
            Long externalRecordId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public enum BulkAction { PUBLISH, HIDE }

    public record BulkRequest(List<Long> missionIds, BulkAction action, String reason) {
    }

    public record PublishResult(Long missionId, MissionStatus status) {
    }
}
