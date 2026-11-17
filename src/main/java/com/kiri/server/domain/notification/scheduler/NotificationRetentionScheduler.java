package com.kiri.server.domain.notification.scheduler;

import com.kiri.server.domain.notification.service.NotificationInboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 생성 후 90일이 지난 알림을 매일 새벽에 삭제한다. 원본 일정·리뷰·미션 데이터는 건드리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetentionScheduler {

    static final int BATCH_SIZE = 1000;
    static final int MAX_BATCHES = 100;

    private final NotificationInboxService notificationInboxService;

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void deleteExpiredNotifications() {
        int total = 0;

        for (int i = 0; i < MAX_BATCHES; i++) {
            int deleted = notificationInboxService.deleteExpiredBatch(BATCH_SIZE);
            total += deleted;

            if (deleted < BATCH_SIZE) {
                break;
            }
        }

        if (total > 0) {
            log.info("보관기간이 지난 알림 삭제 완료 deletedCount={}", total);
        }
    }
}
