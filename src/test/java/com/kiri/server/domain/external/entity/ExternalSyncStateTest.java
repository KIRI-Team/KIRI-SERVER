package com.kiri.server.domain.external.entity;

import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.enums.SyncStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalSyncStateTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 0, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();

    @Test
    @DisplayName("수집 구간을 열면 첫 페이지부터 진행 위치를 저장한다.")
    void openWindow() {
        ExternalSyncState state = state();

        state.openWindow(false, TODAY.minusDays(2), TODAY);

        assertThat(state.hasOpenWindow()).isTrue();
        assertThat(state.getProgressDate()).isEqualTo(TODAY.minusDays(2));
        assertThat(state.getProgressPage()).isEqualTo(1);
        assertThat(state.getWindowEndDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("구간을 끝까지 수집하면 마지막 성공 날짜를 갱신하고 진행 위치와 이전 오류를 지운다.")
    void completeWindow() {
        ExternalSyncState state = state();
        state.openWindow(true, null, TODAY);
        state.recordFailure("EXTERNAL502_2", "timeout");

        state.completeWindow("20261016", NOW);

        assertThat(state.getLastCursor()).isEqualTo("20261016");
        assertThat(state.getLastSuccessAt()).isEqualTo(NOW);
        assertThat(state.getLastStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(state.getLastErrorCode()).isNull();
        assertThat(state.hasOpenWindow()).isFalse();
        assertThat(state.getWindowFull()).isFalse();
    }

    @Test
    @DisplayName("일시 중단과 실패는 진행 위치와 마지막 성공 날짜를 그대로 두고 원인 코드를 남긴다.")
    void pauseAndFailure_keepProgress() {
        ExternalSyncState state = ExternalSyncState.builder()
                .externalSource(ExternalSource.TOUR_API)
                .lastCursor("20261015")
                .build();
        state.openWindow(false, TODAY.minusDays(1), TODAY);
        state.moveTo(TODAY, 3);

        state.recordPause("RUN_BUDGET", "호출 240회 사용 후 일시 중단");

        assertThat(state.getLastStatus()).isEqualTo(SyncStatus.PAUSED);
        assertThat(state.getLastErrorCode()).isEqualTo("RUN_BUDGET");
        assertThat(state.getProgressPage()).isEqualTo(3);

        state.recordFailure("EXTERNAL429_1", "x".repeat(800));

        assertThat(state.getLastStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(state.getLastErrorCode()).isEqualTo("EXTERNAL429_1");
        assertThat(state.getLastError()).hasSize(500);
        assertThat(state.getLastCursor()).isEqualTo("20261015");
        assertThat(state.getProgressDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("하루 호출 수는 같은 날짜에만 누적하고 날짜가 바뀌면 새로 센다.")
    void dailyCalls() {
        ExternalSyncState state = state();

        state.addCalls(TODAY, 100);
        state.addCalls(TODAY, 40);

        assertThat(state.callsUsedOn(TODAY)).isEqualTo(140);
        assertThat(state.callsUsedOn(TODAY.plusDays(1))).isZero();

        state.addCalls(TODAY.plusDays(1), 10);

        assertThat(state.callsUsedOn(TODAY.plusDays(1))).isEqualTo(10);
    }

    private ExternalSyncState state() {
        return ExternalSyncState.builder().externalSource(ExternalSource.TOUR_API).build();
    }
}
