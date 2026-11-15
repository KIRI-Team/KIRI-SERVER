package com.kiri.server.domain.notification.scheduler;

import com.kiri.server.domain.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScheduleD1ReminderSchedulerTest {

    @Mock
    private NotificationService notificationService;

    private ScheduleD1ReminderScheduler scheduler;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneId.of("Asia/Seoul"));

    @BeforeEach
    void setUp() {
        scheduler = new ScheduleD1ReminderScheduler(notificationService, CLOCK);
    }

    @Test
    @DisplayName("D-1 알림 스케줄러는 다음 날 일정을 대상으로 알림을 생성한다")
    void sendD1Reminders() {
        LocalDateTime now = LocalDateTime.now(CLOCK);

        scheduler.sendD1Reminders();

        verify(notificationService).createScheduleD1ReminderNotifications(LocalDate.of(2026, 11, 16), now);
    }
}