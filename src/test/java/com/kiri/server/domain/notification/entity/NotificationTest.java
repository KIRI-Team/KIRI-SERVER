package com.kiri.server.domain.notification.entity;


import com.kiri.server.domain.notification.enums.NotificationPriority;
import com.kiri.server.domain.notification.enums.NotificationStatus;
import com.kiri.server.domain.notification.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {

    private Notification createNotification() {
        return Notification.builder()
                .notificationType(NotificationType.SCHEDULE)
                .title("알림 제목")
                .body("알림 내용")
                .status(NotificationStatus.PENDING)
                .priority(NotificationPriority.P1)
                .build();
    }

    private UUID startProcessing(Notification notification) {
        return notification.startProcessing();
    }

    @Test
    @DisplayName("PENDING 알림은 취소하면 CANCELLED 상태가 된다")
    void cancel() {
        Notification notification = createNotification();

        notification.cancel();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
    }

    @Test
    @DisplayName("PROCESSING 상태가 아닌 알림은 취소되지 않는다")
    void cancelNonPending() {
        Notification notification = createNotification();
        notification.startProcessing();

        notification.cancel();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
    }

    @Test
    @DisplayName("PENDING 알림을 PROCESSING으로 전환하면 Lease ID가 생성된다")
    void startProcessing() {
        Notification notification = createNotification();

        UUID leaseId = notification.startProcessing();

        assertThat(leaseId).isNotNull();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
        assertThat(notification.getProcessingAt()).isNotNull();
        assertThat(notification.getProcessingLeaseId()).isEqualTo(leaseId);
    }

    @Test
    @DisplayName("PENDING이 아닌 알림은 PROCESSING으로 전환되지 않는다")
    void startProcessingNonPending() {
        Notification notification = createNotification();
        notification.startProcessing();

        UUID originalLeaseId = notification.getProcessingLeaseId();

        UUID leaseId = notification.startProcessing();

        assertThat(leaseId).isNull();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
        assertThat(notification.getProcessingLeaseId()).isEqualTo(originalLeaseId);
    }

    @Test
    @DisplayName("올바른 Lease ID로 발송하면 SENT 상태가 된다")
    void send() {
        Notification notification = createNotification();
        UUID leaseId = startProcessing(notification);

        notification.send(leaseId);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isNotNull();
        assertThat(notification.getProcessingAt()).isNull();
        assertThat(notification.getProcessingLeaseId()).isNull();
    }

    @Test
    @DisplayName("잘못된 Lease ID로 발송하면 상태가 변경되지 않는다")
    void sendInvalidLeaseId() {
        Notification notification = createNotification();
        notification.startProcessing();

        notification.send(UUID.randomUUID());

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
        assertThat(notification.getSentAt()).isNull();
        assertThat(notification.getProcessingLeaseId()).isNotNull();
    }

    @Test
    @DisplayName("올바른 Lease ID로 실패 처리하면 재시도 횟수가 증가한다")
    void fail() {
        Notification notification = createNotification();
        UUID leaseId = startProcessing(notification);

        LocalDateTime before = LocalDateTime.now();

        notification.fail(leaseId);

        LocalDateTime after = LocalDateTime.now();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getRetryCount()).isEqualTo(1);
        assertThat(notification.getNextRetryAt()).isBetween(before.plusMinutes(1), after.plusMinutes(1));
        assertThat(notification.getProcessingAt()).isNull();
        assertThat(notification.getProcessingLeaseId()).isNull();
    }

    @Test
    @DisplayName("두 번째 실패 시 재시도 간격은 5분이다")
    void failSecondRetry() {
        Notification notification = createNotification();

        notification.startProcessing();
        notification.fail(notification.getProcessingLeaseId());
        notification.retry();

        UUID leaseId = notification.startProcessing();
        LocalDateTime before = LocalDateTime.now();

        notification.fail(leaseId);

        LocalDateTime after = LocalDateTime.now();

        assertThat(notification.getRetryCount()).isEqualTo(2);
        assertThat(notification.getNextRetryAt()).isBetween(before.plusMinutes(5), after.plusMinutes(5));
    }

    @Test
    @DisplayName("세 번째 실패 시 재시도 간격은 15분이다")
    void failThirdRetry() {
        Notification notification = createNotification();

        notification.startProcessing();
        notification.fail(notification.getProcessingLeaseId());
        notification.retry();

        notification.startProcessing();
        notification.fail(notification.getProcessingLeaseId());
        notification.retry();

        UUID leaseId = notification.startProcessing();
        LocalDateTime before = LocalDateTime.now();

        notification.fail(leaseId);

        LocalDateTime after = LocalDateTime.now();

        assertThat(notification.getRetryCount()).isEqualTo(3);
        assertThat(notification.getNextRetryAt()).isBetween(before.plusMinutes(15), after.plusMinutes(15));
    }

    @Test
    @DisplayName("네 번째 실패부터는 재시도 시각을 설정하지 않는다")
    void failMaxRetryExceeded() {
        Notification notification = createNotification();

        for (int i = 0; i < 4; i++) {
            notification.startProcessing();
            notification.fail(notification.getProcessingLeaseId());

            if (i < 3) {
                notification.retry();
            }
        }

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getRetryCount()).isEqualTo(4);
        assertThat(notification.getNextRetryAt()).isNull();
    }

    @Test
    @DisplayName("잘못된 Lease ID로 실패 처리하면 상태가 변경되지 않는다")
    void failInvalidLeaseId() {
        Notification notification = createNotification();
        notification.startProcessing();

        notification.fail(UUID.randomUUID());

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
        assertThat(notification.getRetryCount()).isZero();
        assertThat(notification.getProcessingLeaseId()).isNotNull();
    }

    @Test
    @DisplayName("올바른 Lease ID로 만료 처리하면 EXPIRED 상태가 된다")
    void expire() {
        Notification notification = createNotification();
        UUID leaseId = startProcessing(notification);

        notification.expire(leaseId);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.EXPIRED);
        assertThat(notification.getProcessingAt()).isNull();
        assertThat(notification.getProcessingLeaseId()).isNull();
    }

    @Test
    @DisplayName("올바른 Lease ID로 건너뛰기 처리하면 SKIPPED 상태가 된다")
    void skipProcessing() {
        Notification notification = createNotification();
        UUID leaseId = startProcessing(notification);

        notification.skip(leaseId);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(notification.getProcessingAt()).isNull();
        assertThat(notification.getProcessingLeaseId()).isNull();
    }

    @Test
    @DisplayName("PENDING 알림은 Lease ID 없이 SKIPPED 처리할 수 있다")
    void skipPending() {
        Notification notification = createNotification();

        notification.skip();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
    }

    @Test
    @DisplayName("PROCESSING 알림을 복구하면 PENDING 상태가 된다")
    void recoverProcessing() {
        Notification notification = createNotification();
        notification.startProcessing();

        notification.recoverProcessing();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getProcessingAt()).isNull();
        assertThat(notification.getProcessingLeaseId()).isNull();
    }

    @Test
    @DisplayName("재시도 가능한 FAILED 알림은 PENDING 상태로 복구된다")
    void retry() {
        Notification notification = createNotification();
        notification.startProcessing();
        notification.fail(notification.getProcessingLeaseId());

        notification.retry();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getRetryCount()).isEqualTo(1);
        assertThat(notification.getNextRetryAt()).isNull();
    }

    @Test
    @DisplayName("재시도 시각이 없는 FAILED 알림은 PENDING으로 복구되지 않는다")
    void retryWithoutNextRetryAt() {
        Notification notification = createNotification();

        for (int i = 0; i < 4; i++) {
            notification.startProcessing();
            notification.fail(notification.getProcessingLeaseId());

            if (i < 3) {
                notification.retry();
            }
        }

        notification.retry();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getRetryCount()).isEqualTo(4);
        assertThat(notification.getNextRetryAt()).isNull();
    }

    @Test
    @DisplayName("올바른 Lease ID로 알림을 지연하면 PENDING 상태와 예약 시각이 설정된다")
    void defer() {
        Notification notification = createNotification();
        UUID leaseId = startProcessing(notification);
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 11, 16, 9, 0);

        notification.defer(leaseId, scheduledAt);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getScheduledAt()).isEqualTo(scheduledAt);
        assertThat(notification.getProcessingAt()).isNull();
        assertThat(notification.getProcessingLeaseId()).isNull();
    }

    @Test
    @DisplayName("잘못된 Lease ID로 알림을 지연하면 상태가 변경되지 않는다")
    void deferInvalidLeaseId() {
        Notification notification = createNotification();
        notification.startProcessing();

        notification.defer(UUID.randomUUID(), LocalDateTime.of(2026, 11, 16, 9, 0));

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
        assertThat(notification.getScheduledAt()).isNull();
    }
}