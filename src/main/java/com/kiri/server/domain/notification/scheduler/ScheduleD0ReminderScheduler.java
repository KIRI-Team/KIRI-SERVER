package com.kiri.server.domain.notification.scheduler;

import com.kiri.server.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ScheduleD0ReminderScheduler {

    private final NotificationService notificationService;
    private final Clock clock;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void sendD0Reminders() {
        LocalDateTime now = LocalDateTime.now(clock);

        notificationService.createScheduleD0ReminderNotifications(now.toLocalDate(), now.toLocalDate().plusDays(1), now);
    }
}
