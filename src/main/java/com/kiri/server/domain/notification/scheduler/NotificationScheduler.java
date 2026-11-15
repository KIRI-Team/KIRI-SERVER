package com.kiri.server.domain.notification.scheduler;

import com.kiri.server.domain.notification.service.NotificationDispatchService;
import com.kiri.server.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationService notificationService;
    private final NotificationDispatchService notificationDispatchService;

    @Scheduled(fixedDelay = 10000)
    public void dispatchPendingNotifications() {
        notificationDispatchService.dispatchPendingNotifications();
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void recoverStaleProcessingNotifications() {
        int recoveredCount = notificationService.recoverStaleProcessingNotifications();

        if (recoveredCount > 0) {
            log.info("중단된 알림 복구 스케줄러 실행 완료 recoveredCount={}", recoveredCount);
        }
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void retryFailedNotifications() {
        notificationService.retryFailedNotifications();
    }
}
