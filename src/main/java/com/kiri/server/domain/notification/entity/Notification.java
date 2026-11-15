package com.kiri.server.domain.notification.entity;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.notification.enums.NotificationPriority;
import com.kiri.server.domain.notification.enums.NotificationStatus;
import com.kiri.server.domain.notification.enums.NotificationType;
import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "notification",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notification_member_event_key", columnNames = {"member_id", "event_key"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", length = 30, nullable = false)
    private NotificationType notificationType;

    @Column(name = "title", length = 100, nullable = false)
    private String title;

    @Column(name = "body", length = 255, nullable = false)
    private String body;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private NotificationStatus status;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "reference_version")
    private Integer referenceVersion;

    @Column(name = "reference_date")
    private LocalDate referenceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 2, nullable = false)
    private NotificationPriority priority;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "event_key", length = 200)
    private String eventKey;

    @Column(name = "processing_at")
    private LocalDateTime processingAt;

    @Column(name = "processing_lease_id")
    private UUID processingLeaseId;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    public void cancel() {
        if (this.status == NotificationStatus.PENDING) {
            this.status = NotificationStatus.CANCELLED;
        }
    }

    public UUID startProcessing() {
        if (this.status != NotificationStatus.PENDING) {
            return null;
        }

        UUID leaseId = UUID.randomUUID();

        this.status = NotificationStatus.PROCESSING;
        this.processingAt = LocalDateTime.now();
        this.processingLeaseId = leaseId;

        return leaseId;
    }

    public void send(UUID leaseId) {
        if (!isProcessing(leaseId)) {
            return;
        }

        this.status = NotificationStatus.SENT;
        this.sentAt = LocalDateTime.now();
        clearProcessing();
    }

    public void fail(UUID leaseId) {
        if (!isProcessing(leaseId)) {
            return;
        }

        this.status = NotificationStatus.FAILED;
        this.retryCount++;

        this.nextRetryAt = switch (this.retryCount) {
            case 1 -> LocalDateTime.now().plusMinutes(1);
            case 2 -> LocalDateTime.now().plusMinutes(5);
            case 3 -> LocalDateTime.now().plusMinutes(15);
            default -> null;
        };

        clearProcessing();
    }

    public void expire(UUID leaseId) {
        if (!isProcessing(leaseId)) {
            return;
        }

        this.status = NotificationStatus.EXPIRED;
        clearProcessing();
    }

    public void skip() {
        if (this.status == NotificationStatus.PENDING) {
            this.status = NotificationStatus.SKIPPED;
        }
    }

    public void skip(UUID leaseId) {
        if (!isProcessing(leaseId)) {
            return;
        }

        this.status = NotificationStatus.SKIPPED;
        clearProcessing();
    }

    public void recoverProcessing() {
        if (this.status != NotificationStatus.PROCESSING) {
            return;
        }

        this.status = NotificationStatus.PENDING;
        clearProcessing();
    }

    public void retry() {
        if (this.status != NotificationStatus.FAILED || this.nextRetryAt == null) {
            return;
        }

        this.status = NotificationStatus.PENDING;
        this.nextRetryAt = null;
    }

    public void defer(UUID leaseId, LocalDateTime scheduledAt) {
        if (!isProcessing(leaseId)) {
            return;
        }

        this.status = NotificationStatus.PENDING;
        this.scheduledAt = scheduledAt;
        clearProcessing();
    }

    private boolean isProcessing(UUID leaseId) {
        return this.status == NotificationStatus.PROCESSING
                && this.processingLeaseId != null
                && this.processingLeaseId.equals(leaseId);
    }

    private void clearProcessing() {
        this.processingAt = null;
        this.processingLeaseId = null;
    }

    @PrePersist
    protected void assignPriority() {
        if (priority == null && notificationType != null) {
            priority = notificationType.getPriority();
        }
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
