package com.kiri.server.domain.external.scheduler;

import com.kiri.server.domain.external.service.TourApiSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TourApiSyncScheduler {

    private final TourApiSyncService tourApiSyncService;

    @Scheduled(cron = "${tour-api.sync-cron:0 0 */6 * * *}", zone = "Asia/Seoul")
    public void sync() {
        tourApiSyncService.sync();
    }
}
