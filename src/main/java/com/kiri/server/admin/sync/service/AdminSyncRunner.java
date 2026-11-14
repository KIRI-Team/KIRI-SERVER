package com.kiri.server.admin.sync.service;

import com.kiri.server.admin.sync.dto.SyncAdminDTO;
import com.kiri.server.domain.external.dto.SyncResult;
import com.kiri.server.domain.external.service.TourApiSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

// 수동 실행은 오래 걸리므로 요청 스레드와 분리한다. 중복 실행은 TourApiSyncService의 잠금이 막는다.
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSyncRunner {

    private final TourApiSyncService tourApiSyncService;

    @Async
    public void runTourApi(SyncAdminDTO.RunMode mode) {
        try {
            SyncResult result = mode == SyncAdminDTO.RunMode.FULL_CHECK ? tourApiSyncService.fullCheck() : tourApiSyncService.sync();
            log.info("[AdminSync] 수동 실행 종료: mode={}, result={}", mode, result);
        } catch (RuntimeException e) {
            log.error("[AdminSync] 수동 실행 실패: mode={}", mode, e);
        }
    }
}
