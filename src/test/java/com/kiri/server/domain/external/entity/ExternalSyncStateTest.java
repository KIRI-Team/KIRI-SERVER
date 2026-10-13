package com.kiri.server.domain.external.entity;

import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.enums.SyncStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalSyncStateTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 6, 0);

    @Test
    @DisplayName("수집에 성공하면 기준값을 전진시키고 이전 실패 사유를 지운다.")
    void recordSuccess() {
        ExternalSyncState state = ExternalSyncState.builder()
                .externalSource(ExternalSource.TOUR_API)
                .lastCursor("20261012000000")
                .lastStatus(SyncStatus.FAILED)
                .lastError("timeout")
                .build();

        state.recordSuccess("20261013060000", NOW);

        assertThat(state.getLastCursor()).isEqualTo("20261013060000");
        assertThat(state.getLastSuccessAt()).isEqualTo(NOW);
        assertThat(state.getLastStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(state.getLastError()).isNull();
    }

    @Test
    @DisplayName("수집에 실패하면 기준값과 마지막 성공 시각은 그대로 둔다.")
    void recordFailure_keepsCursor() {
        ExternalSyncState state = ExternalSyncState.builder()
                .externalSource(ExternalSource.TOUR_API)
                .lastCursor("20261012000000")
                .lastSuccessAt(NOW.minusHours(6))
                .build();

        state.recordFailure("timeout");

        assertThat(state.getLastCursor()).isEqualTo("20261012000000");
        assertThat(state.getLastSuccessAt()).isEqualTo(NOW.minusHours(6));
        assertThat(state.getLastStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(state.getLastError()).isEqualTo("timeout");
    }

    @Test
    @DisplayName("실패 사유가 500자를 넘으면 컬럼 길이에 맞게 자른다.")
    void recordFailure_truncatesLongError() {
        ExternalSyncState state = ExternalSyncState.builder()
                .externalSource(ExternalSource.TOUR_API)
                .build();

        state.recordFailure("x".repeat(800));

        assertThat(state.getLastError()).hasSize(500);
    }
}
