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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleInvitationReminderSchedulerTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private NotificationService notificationService;

    private ScheduleInvitationReminderScheduler scheduler;

    private final LocalDateTime now = LocalDateTime.of(2026, 11, 15, 10, 0);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(now.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));

        scheduler = new ScheduleInvitationReminderScheduler(scheduleRepository, notificationService, clock);
    }

    @Test
    @DisplayName("리마인더 발송 대상 일정을 조회하고 알림 생성을 요청한다")
    void sendInvitationRemindersSuccess() {
        Schedule schedule1 = Schedule.builder()
                .id(1L)
                .build();

        Schedule schedule2 = Schedule.builder()
                .id(2L)
                .build();

        given(scheduleRepository
                .findAllByStatusAndResponseReminderAtLessThanEqualOrderByResponseReminderAtAsc(ScheduleStatus.PENDING_RESPONSE, now))
                .willReturn(List.of(schedule1, schedule2));

        scheduler.sendInvitationReminders();

        verify(scheduleRepository).findAllByStatusAndResponseReminderAtLessThanEqualOrderByResponseReminderAtAsc(ScheduleStatus.PENDING_RESPONSE, now);

        verify(notificationService).createScheduleInvitationReminderNotifications(1L, now);

        verify(notificationService).createScheduleInvitationReminderNotifications(2L, now);
    }

    @Test
    @DisplayName("리마인더 발송 대상 일정이 없으면 알림을 생성하지 않는다")
    void sendInvitationRemindersNoSchedules() {
        given(scheduleRepository
                .findAllByStatusAndResponseReminderAtLessThanEqualOrderByResponseReminderAtAsc(ScheduleStatus.PENDING_RESPONSE, now))
                .willReturn(List.of());

        scheduler.sendInvitationReminders();

        verify(notificationService, never()).createScheduleInvitationReminderNotifications(anyLong(), any());
    }

    @Test
    @DisplayName("한 일정의 알림 생성에 실패해도 다음 일정은 처리한다")
    void sendInvitationRemindersContinueOnFailure() {
        Schedule schedule1 = Schedule.builder()
                .id(1L)
                .build();

        Schedule schedule2 = Schedule.builder()
                .id(2L)
                .build();

        given(scheduleRepository
                .findAllByStatusAndResponseReminderAtLessThanEqualOrderByResponseReminderAtAsc(ScheduleStatus.PENDING_RESPONSE, now))
                .willReturn(List.of(schedule1, schedule2));

        doThrow(new RuntimeException("알림 생성 실패"))
                .when(notificationService)
                .createScheduleInvitationReminderNotifications(1L, now);

        scheduler.sendInvitationReminders();

        verify(notificationService).createScheduleInvitationReminderNotifications(1L, now);

        verify(notificationService).createScheduleInvitationReminderNotifications(2L, now);
    }
}