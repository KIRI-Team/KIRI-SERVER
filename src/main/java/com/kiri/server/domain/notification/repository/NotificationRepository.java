package com.kiri.server.domain.notification.repository;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.notification.entity.Notification;
import com.kiri.server.domain.notification.enums.NotificationStatus;
import com.kiri.server.domain.notification.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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
}
