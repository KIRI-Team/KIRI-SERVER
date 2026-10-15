package com.kiri.server.domain.external.scheduler;

import com.kiri.server.domain.external.service.TourApiSyncService;
import com.kiri.server.domain.mission.service.MissionGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TourApiSyncScheduler {

    private final TourApiSyncService tourApiSyncService;
    private final MissionGenerationService missionGenerationService;
    private final Clock clock;

    @Scheduled(cron = "${tour-api.sync-cron:0 0 0,12 * * *}", zone = "Asia/Seoul")
    public void sync() {
        tourApiSyncService.sync();
    }

    @Scheduled(cron = "${mission.end-cron:0 10 0 * * *}", zone = "Asia/Seoul")
    public void endExpiredMissions() {
        LocalDateTime now = LocalDateTime.now(clock);
        int ended = missionGenerationService.endExpiredMissions(now.toLocalDate(), now);
        int published = missionGenerationService.publishPendingMissions(now.toLocalDate(), now);
        log.info("[Mission] 기간 종료 {}건, 공개 대기 해제 {}건", ended, published);
    }
}
