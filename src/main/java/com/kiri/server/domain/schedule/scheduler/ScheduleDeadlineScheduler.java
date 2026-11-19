package com.kiri.server.domain.schedule.scheduler;

import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.domain.schedule.repository.ScheduleRepository;
import com.kiri.server.domain.schedule.service.ScheduleResponseService;
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
public class ScheduleDeadlineScheduler {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleResponseService scheduleResponseService;
    private final Clock clock;

    @Scheduled(cron = "${schedule.deadline-cron:0 */5 * * * *}", zone = "Asia/Seoul")
    public void finalizeExpiredSchedules() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> expiredIds = scheduleRepository
                .findIdsByStatusAndDeadlineBefore(ScheduleStatus.PENDING_RESPONSE, now);
        for (Long scheduleId : expiredIds) {
            try {
                scheduleResponseService.finalizeExpired(scheduleId, now);
            } catch (Exception e) {
                log.error("응답 마감 일정 상태 계산 실패 scheduleId={}", scheduleId, e);
            }
        }
    }
}
