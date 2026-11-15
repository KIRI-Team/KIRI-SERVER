package com.kiri.server.domain.notification.scheduler;

import com.kiri.server.domain.notification.service.NotificationService;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.domain.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleReconfirmReminderSchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 9, 0);

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private NotificationService notificationService;

    private ScheduleReconfirmReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));

        scheduler = new ScheduleReconfirmReminderScheduler(scheduleRepository, notificationService, clock);
    }

    private Schedule schedule(Long id) {
        return Schedule.builder()
                .id(id)
                .build();
    }

    @Test
    @DisplayName("예약 시각이 지난 일정들을 조회하고 각 일정의 재확인 알림을 생성한다.")
    void sendReconfirmReminders_success() {
        Schedule schedule1 = schedule(1L);
        Schedule schedule2 = schedule(2L);

        given(scheduleRepository.findAllByReconfirmReminderAtLessThanEqualAndStatusInOrderByReconfirmReminderAtAsc(NOW, List.of(ScheduleStatus.PENDING_RESPONSE, ScheduleStatus.CONFIRMED))).willReturn(List.of(schedule1, schedule2));

        scheduler.sendReconfirmReminders();

        verify(scheduleRepository).findAllByReconfirmReminderAtLessThanEqualAndStatusInOrderByReconfirmReminderAtAsc(NOW, List.of(ScheduleStatus.PENDING_RESPONSE, ScheduleStatus.CONFIRMED));

        verify(notificationService).createScheduleReconfirmReminderNotifications(1L, NOW);

        verify(notificationService).createScheduleReconfirmReminderNotifications(2L, NOW);
    }

    @Test
    @DisplayName("재확인 리마인더 대상이 없으면 알림을 생성하지 않는다.")
    void sendReconfirmReminders_noSchedules() {
        given(scheduleRepository.findAllByReconfirmReminderAtLessThanEqualAndStatusInOrderByReconfirmReminderAtAsc(NOW, List.of(ScheduleStatus.PENDING_RESPONSE, ScheduleStatus.CONFIRMED))).willReturn(List.of());

        scheduler.sendReconfirmReminders();

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("한 일정의 알림 생성에 실패해도 나머지 일정의 알림 생성을 계속한다.")
    void sendReconfirmReminders_continuesWhenOneScheduleFails() {
        Schedule schedule1 = schedule(1L);
        Schedule schedule2 = schedule(2L);

        given(scheduleRepository.findAllByReconfirmReminderAtLessThanEqualAndStatusInOrderByReconfirmReminderAtAsc(NOW, List.of(ScheduleStatus.PENDING_RESPONSE, ScheduleStatus.CONFIRMED))).willReturn(List.of(schedule1, schedule2));

        doThrow(new RuntimeException("알림 생성 실패")).when(notificationService).createScheduleReconfirmReminderNotifications(1L, NOW);

        scheduler.sendReconfirmReminders();

        verify(notificationService).createScheduleReconfirmReminderNotifications(1L, NOW);

        verify(notificationService).createScheduleReconfirmReminderNotifications(2L, NOW);
    }

    @Test
    @DisplayName("조회 시 PENDING_RESPONSE와 CONFIRMED 상태를 모두 대상으로 지정한다.")
    void sendReconfirmReminders_queriesActiveStatuses() {
        scheduler.sendReconfirmReminders();

        verify(scheduleRepository).findAllByReconfirmReminderAtLessThanEqualAndStatusInOrderByReconfirmReminderAtAsc(NOW, List.of(ScheduleStatus.PENDING_RESPONSE, ScheduleStatus.CONFIRMED));
    }
}