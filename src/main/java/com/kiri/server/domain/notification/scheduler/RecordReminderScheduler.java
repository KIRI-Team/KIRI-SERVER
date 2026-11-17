package com.kiri.server.domain.notification.scheduler;

import com.kiri.server.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 활동 후 기록 작성 안내. 알림 정책 최종 결정 5번에 따라 KIRI 1.0에서는 자동 발송하지 않는다.
 * 다시 켜려면 notification.record-reminder.enabled=true 로 설정한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "notification.record-reminder.enabled", havingValue = "true")
@RequiredArgsConstructor
public class RecordReminderScheduler {

    private final NotificationService notificationService;
    private final Clock clock;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void createRecordReminderNotifications() {
        LocalDateTime now = LocalDateTime.now(clock);

        log.info("활동 기록 알림 스케줄러 시작 now={}", now);

        notificationService.createRecordReminderNotifications(now);

        log.info("활동 기록 알림 스케줄러 종료 now={}", now);
    }
}
