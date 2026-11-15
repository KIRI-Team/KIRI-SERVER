package com.kiri.server.domain.notification.scheduler;

import com.kiri.server.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
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
