package com.kiri.server.domain.notification.scheduler;

import com.kiri.server.domain.notification.service.NotificationService;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.domain.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleReconfirmReminderScheduler {

    private final ScheduleRepository scheduleRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void sendReconfirmReminders() {
        LocalDateTime now = LocalDateTime.now(clock);

        List<Schedule> schedules = scheduleRepository.findAllByReconfirmReminderAtLessThanEqualAndStatusInOrderByReconfirmReminderAtAsc(now, List.of(ScheduleStatus.PENDING_RESPONSE, ScheduleStatus.CONFIRMED));

        for (Schedule schedule : schedules) {
            try {
                notificationService.createScheduleReconfirmReminderNotifications(schedule.getId(), now);
            } catch (Exception e) {
                log.error("일정 재확인 리마인더 생성 실패 scheduleId={}", schedule.getId(), e);
            }
        }
    }
}
