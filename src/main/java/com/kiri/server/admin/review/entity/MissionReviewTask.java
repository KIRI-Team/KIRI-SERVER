package com.kiri.server.admin.review.entity;

import com.kiri.server.admin.review.enums.ReviewTaskStatus;
import com.kiri.server.domain.external.enums.ReviewReason;
import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDateTime;

// 검수 업무 상태. 원본 처리 상태·미션 공개 상태와 따로 관리한다.
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "mission_review_task")
public class MissionReviewTask extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mission_review_task_id")
    private Long id;

    @Column(name = "external_record_id", nullable = false)
    private Long externalRecordId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ReviewTaskStatus status = ReviewTaskStatus.OPEN;

    @Column(name = "assignee_admin_id")
    private Long assigneeAdminId;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_reason", length = 20)
    private ReviewReason reviewReason;

    @Column(name = "note", length = 500)
    private String note;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "applied_overrides", columnDefinition = "jsonb")
    private String appliedOverrides;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public static MissionReviewTask open(Long externalRecordId, ReviewReason reason) {
        return MissionReviewTask.builder().externalRecordId(externalRecordId).reviewReason(reason).build();
    }

    public boolean isOpen() {
        return status == ReviewTaskStatus.OPEN || status == ReviewTaskStatus.ON_HOLD;
    }

    public void assign(Long adminId) {
        this.assigneeAdminId = adminId;
    }

    public void hold(String note) {
        this.status = ReviewTaskStatus.ON_HOLD;
        this.note = note;
    }

    public void approve(Long adminId, String appliedOverrides, String note, LocalDateTime now) {
        this.status = ReviewTaskStatus.APPROVED;
        this.appliedOverrides = appliedOverrides;
        resolve(adminId, note, now);
    }

    public void reject(Long adminId, String note, LocalDateTime now) {
        this.status = ReviewTaskStatus.REJECTED;
        resolve(adminId, note, now);
    }

    private void resolve(Long adminId, String note, LocalDateTime now) {
        this.resolvedBy = adminId;
        this.resolvedAt = now;
        if (note != null && !note.isBlank()) {
            this.note = note;
        }
    }
}
