package com.kiri.server.domain.notification.service;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.kiri.server.domain.member.entity.MemberDevice;
import com.kiri.server.domain.member.repository.MemberDeviceRepository;
import com.kiri.server.domain.notification.dto.NotificationResDTO;
import com.kiri.server.domain.notification.enums.NotificationPriority;
import com.kiri.server.domain.notification.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchService {

    private final FcmService fcmService;
    private final MemberDeviceRepository memberDeviceRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public void dispatchPendingNotifications() {
        List<NotificationResDTO.NotificationProcessingTarget> targets = notificationService.claimPendingNotifications();

        for (NotificationResDTO.NotificationProcessingTarget target : targets) {
            dispatch(target);
        }
    }

    private void dispatch(NotificationResDTO.NotificationProcessingTarget target) {

        LocalDateTime now = LocalDateTime.now(clock);

        if (target.notificationType() == NotificationType.QUESTION_REMINDER && target.referenceDate() != null && target.referenceDate().isBefore(now.toLocalDate())) {
            notificationService.expireNotification(target.notificationId(), target.leaseId());
            return;
        }

        // 발송 직전에 회원 상태를 다시 확인한다. 푸시 OFF·가족 탈퇴·회원 탈퇴면 인앱 기록만 남긴다.
        if (notificationService.isPushBlocked(target.memberId())) {
            notificationService.markNotificationSkipped(target.notificationId(), target.leaseId());
            return;
        }

        if (isQuietHours(now) &&target.notificationType().getPriority().getValue() >= 2) {
            notificationService.deferNotification(target.notificationId(), target.leaseId(), getNextAllowedTime(now));
            return;
        }

        if (target.notificationType().getPriority() == NotificationPriority.P5 && notificationService.hasReachedDailyPushLimit(target.memberId())) {

            LocalDateTime nextAllowedTime = getNextAllowedTime(now);

            notificationService.deferNotification(target.notificationId(), target.leaseId(), nextAllowedTime);

            log.info("일일 푸시 발송 제한 초과로 알림 지연 notificationId={}, memberId={}, scheduledAt={}", target.notificationId(), target.memberId(), nextAllowedTime);
            return;
        }

        List<MemberDevice> devices = memberDeviceRepository.findAllByMemberId(target.memberId());

        if (devices.isEmpty()) {
            notificationService.markNotificationSkipped(target.notificationId(), target.leaseId());
            return;
        }

        boolean sent = false;
        boolean failed = false;

        for (MemberDevice device : devices) {
            try {
                fcmService.send(device.getFcmToken(), target.title(), target.body(), target.notificationType());
                sent = true;
            } catch (FirebaseMessagingException e) {
                MessagingErrorCode errorCode = e.getMessagingErrorCode();

                log.warn("FCM 전송 실패 notificationId={}, errorCode={}", target.notificationId(), errorCode, e);

                if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.SENDER_ID_MISMATCH) {
                    memberDeviceRepository.deleteByFcmToken(device.getFcmToken());
                } else {
                    failed = true;
                }
            }
        }

        if (sent) {
            notificationService.markNotificationSent(target.notificationId(), target.leaseId());
        } else if (failed) {
            notificationService.markNotificationFailed(target.notificationId(), target.leaseId());
        } else {
            notificationService.markNotificationSkipped(target.notificationId(), target.leaseId());
        }
    }

    private boolean isQuietHours(LocalDateTime now) {
        LocalTime time = now.toLocalTime();
        return !time.isBefore(LocalTime.of(22, 0)) || time.isBefore(LocalTime.of(9, 0));
    }

    private LocalDateTime getNextAllowedTime(LocalDateTime now) {
        LocalDate date = now.toLocalTime().isBefore(LocalTime.of(9, 0)) ? now.toLocalDate() : now.toLocalDate().plusDays(1);
        return date.atTime(9, 0);
    }
}
