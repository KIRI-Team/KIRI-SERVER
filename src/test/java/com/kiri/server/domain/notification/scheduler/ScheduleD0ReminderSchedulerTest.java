package com.kiri.server.domain.notification.scheduler;

import com.kiri.server.domain.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ScheduleD0ReminderSchedulerTest {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");

    @Mock
    private NotificationService notificationService;

    private ScheduleD0ReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZONE_ID);

        scheduler = new ScheduleD0ReminderScheduler(notificationService, clock);
    }

    @Test
    @DisplayName("D0 알림 스케줄러는 오늘부터 내일까지의 일정을 조회한다.")
    void sendD0RemindersSuccess() {
        LocalDate today = LocalDate.of(2026, 11, 15);
        LocalDate tomorrow = today.plusDays(1);
        LocalDateTime now = LocalDateTime.of(2026, 11, 15, 19, 0);

        scheduler.sendD0Reminders();

        then(notificationService).should().createScheduleD0ReminderNotifications(today, tomorrow, now);
    }
}