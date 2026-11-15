package com.kiri.server.domain.notification.repository;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.notification.entity.Notification;
import com.kiri.server.domain.notification.enums.NotificationPriority;
import com.kiri.server.domain.notification.enums.NotificationStatus;
import com.kiri.server.domain.notification.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    void deleteAllByMemberId(Long memberId);
    boolean existsByMemberAndNotificationTypeAndReferenceDate(Member member, NotificationType notificationType, LocalDate referenceDate);

    List<Notification> findAllByStatus(NotificationStatus status);

    Optional<Notification> findByMemberIdAndNotificationTypeAndReferenceDateAndStatus(Long memberId, NotificationType notificationType, LocalDate referenceDate, NotificationStatus status);

    List<Notification> findAllByMemberAndNotificationTypeAndReferenceIdAndStatus(Member member, NotificationType notificationType, Long referenceId, NotificationStatus status);

    boolean existsByMemberAndNotificationTypeAndReferenceId(Member member, NotificationType notificationType, Long referenceId);

    List<Notification> findAllByNotificationTypeAndReferenceIdAndStatus(NotificationType notificationType, Long referenceId, NotificationStatus status);

    boolean existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(Member member, NotificationType notificationType, Long referenceId, Integer referenceVersion);

    List<Notification> findAllByReferenceIdAndStatus(Long referenceId, NotificationStatus status);

    List<Notification> findAllByNotificationTypeInAndReferenceIdAndStatus(Collection<NotificationType> notificationTypes, Long referenceId, NotificationStatus status);

    @Query(value = """
            SELECT *
            FROM notification
            WHERE status = 'PENDING'
              AND (scheduled_at IS NULL OR scheduled_at <= :now)
            ORDER BY
                priority ASC,
                scheduled_at ASC NULLS FIRST,
                notification_id ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Notification> findPendingNotificationsForProcessing(@Param("now") LocalDateTime now, @Param("batchSize") int batchSize);

    @Query(value = """
            SELECT *
            FROM notification
            WHERE status = 'PROCESSING'
              AND processing_at <= :threshold
            ORDER BY processing_at ASC, notification_id ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Notification> findStaleProcessingNotifications(@Param("threshold") LocalDateTime threshold, @Param("batchSize") int batchSize);

    @Query(value = """
            SELECT *
            FROM notification
            WHERE status = 'FAILED'
              AND next_retry_at IS NOT NULL
              AND next_retry_at <= :now
            ORDER BY next_retry_at ASC, notification_id ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Notification> findRetryableNotifications(@Param("now") LocalDateTime now, @Param("batchSize") int batchSize);

    long countByMember_IdAndPriorityAndStatusAndSentAtGreaterThanEqualAndSentAtLessThan(Long memberId, NotificationPriority priority, NotificationStatus status, LocalDateTime start, LocalDateTime end);

    Optional<Notification> findByMemberIdAndEventKey(Long memberId, String eventKey);

    boolean existsByMemberIdAndEventKey(Long memberId, String eventKey);
}
