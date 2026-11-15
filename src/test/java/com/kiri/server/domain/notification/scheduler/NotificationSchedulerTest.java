package com.kiri.server.domain.notification.scheduler;

import com.kiri.server.domain.notification.service.NotificationDispatchService;
import com.kiri.server.domain.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @InjectMocks
    private NotificationScheduler notificationScheduler;

    @Test
    @DisplayName("대기 중인 알림 발송 스케줄러가 실행된다")
    void dispatchPendingNotifications() {
        notificationScheduler.dispatchPendingNotifications();

        verify(notificationDispatchService).dispatchPendingNotifications();
        verifyNoMoreInteractions(notificationService, notificationDispatchService);
    }

    @Test
    @DisplayName("처리 중단된 알림 복구 스케줄러가 실행된다")
    void recoverStaleProcessingNotifications() {
        given(notificationService.recoverStaleProcessingNotifications()).willReturn(3);

        notificationScheduler.recoverStaleProcessingNotifications();

        verify(notificationService).recoverStaleProcessingNotifications();
        verifyNoMoreInteractions(notificationService, notificationDispatchService);
    }

    @Test
    @DisplayName("복구할 알림이 없어도 복구 스케줄러가 정상 실행된다")
    void recoverStaleProcessingNotificationsWhenNoneRecovered() {
        given(notificationService.recoverStaleProcessingNotifications()).willReturn(0);

        notificationScheduler.recoverStaleProcessingNotifications();

        verify(notificationService).recoverStaleProcessingNotifications();
        verifyNoMoreInteractions(notificationService, notificationDispatchService);
    }

    @Test
    @DisplayName("실패한 알림 재시도 스케줄러가 실행된다")
    void retryFailedNotifications() {
        given(notificationService.retryFailedNotifications()).willReturn(2);

        notificationScheduler.retryFailedNotifications();

        verify(notificationService).retryFailedNotifications();
        verifyNoMoreInteractions(notificationService, notificationDispatchService);
    }
}