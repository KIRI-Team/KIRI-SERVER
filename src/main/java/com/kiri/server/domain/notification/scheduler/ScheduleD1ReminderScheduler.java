package com.kiri.server.domain.notification.scheduler;

import com.kiri.server.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ScheduleD1ReminderScheduler {

    private final NotificationService notificationService;
    private final Clock clock;

    @Scheduled(cron = "0 0 19 * * *", zone = "Asia/Seoul")
    public void sendD1Reminders() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime cutoff = now.toLocalDate().atTime(19, 0);

        notificationService.createScheduleD1ReminderNotifications(now.toLocalDate().plusDays(1), cutoff);
    }
}
