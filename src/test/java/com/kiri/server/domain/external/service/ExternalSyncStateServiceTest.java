package com.kiri.server.domain.external.service;

import com.kiri.server.domain.external.dto.SyncWindow;
import com.kiri.server.domain.external.entity.ExternalSyncState;
import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.repository.ExternalSyncStateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ExternalSyncStateServiceTest {

    private static final ExternalSource SOURCE = ExternalSource.TOUR_API;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    @Mock
    private ExternalSyncStateRepository externalSyncStateRepository;

    @InjectMocks
    private ExternalSyncStateService externalSyncStateService;

    @Test
    @DisplayName("성공 날짜가 없으면 전체 수집 구간을 연다.")
    void begin_fullWhenNoCursor() {
        ExternalSyncState state = givenState(null);

        SyncWindow window = externalSyncStateService.begin(SOURCE, TODAY);

        assertThat(window.full()).isTrue();
        assertThat(window.endDate()).isEqualTo(TODAY);
        assertThat(window.startPage()).isEqualTo(1);
        assertThat(window.resumed()).isFalse();
        assertThat(state.hasOpenWindow()).isTrue();
    }

    @Test
    @DisplayName("최근 성공 날짜가 있으면 그날부터 오늘까지 증분 구간을 연다.")
    void begin_incremental() {
        givenState("20261014");

        SyncWindow window = externalSyncStateService.begin(SOURCE, TODAY);

        assertThat(window.full()).isFalse();
        assertThat(window.fromDate()).isEqualTo(LocalDate.of(2026, 10, 14));
    }

    @Test
    @DisplayName("성공 날짜가 30일보다 오래됐거나 미래면 전체 수집으로 돌린다.")
    void begin_fullWhenCursorOutOfRange() {
        givenState("20260901");
        assertThat(externalSyncStateService.begin(SOURCE, TODAY).full()).isTrue();

        givenState("20261020");
        assertThat(externalSyncStateService.begin(SOURCE, TODAY).full()).isTrue();
    }

    @Test
    @DisplayName("열린 구간이 있으면 저장된 페이지 한 장 앞에서 이어 가고 구간 종료일은 그대로 둔다.")
    void begin_resume() {
        ExternalSyncState state = givenState("20261010");
        state.openWindow(false, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 15));
        state.moveTo(LocalDate.of(2026, 10, 12), 4);
        state.addCalls(TODAY, 120);

        SyncWindow window = externalSyncStateService.begin(SOURCE, TODAY);

        assertThat(window.resumed()).isTrue();
        assertThat(window.fromDate()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(window.endDate()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(window.startPage()).isEqualTo(3);
        assertThat(window.callsUsedToday()).isEqualTo(120);
    }

    @Test
    @DisplayName("구간을 끝까지 돌면 구간 종료일을 다음 성공 날짜로 저장하고 진행 위치를 지운다.")
    void complete() {
        ExternalSyncState state = givenState(null);
        externalSyncStateService.begin(SOURCE, TODAY);

        externalSyncStateService.complete(SOURCE, TODAY, LocalDateTime.of(2026, 10, 16, 0, 30));

        assertThat(state.getLastCursor()).isEqualTo("20261016");
        assertThat(state.hasOpenWindow()).isFalse();
    }

    @Test
    @DisplayName("잘못된 성공 날짜 값은 없는 것으로 본다.")
    void parseCursor_invalid() {
        assertThat(ExternalSyncStateService.parseCursor("2026-10-16")).isNull();
        assertThat(ExternalSyncStateService.parseCursor(" ")).isNull();
    }

    private ExternalSyncState givenState(String cursor) {
        ExternalSyncState state = ExternalSyncState.builder()
                .externalSource(SOURCE)
                .lastCursor(cursor)
                .build();
        given(externalSyncStateRepository.findByExternalSource(SOURCE)).willReturn(Optional.of(state));
        return state;
    }
}
