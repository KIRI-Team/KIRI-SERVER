package com.kiri.server.domain.external.service;

import com.kiri.server.domain.external.dto.SyncWindow;
import com.kiri.server.domain.external.entity.ExternalSyncState;
import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.repository.ExternalSyncStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Service
@RequiredArgsConstructor
public class ExternalSyncStateService {

    private static final DateTimeFormatter CURSOR_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int MAX_INCREMENTAL_DAYS = 30;
    private static final Duration LOCK_TIMEOUT = Duration.ofHours(3);

    private final ExternalSyncStateRepository externalSyncStateRepository;

    @Transactional
    public boolean tryLock(ExternalSource source, LocalDateTime now) {
        getOrCreate(source);
        return externalSyncStateRepository.acquireLock(source, now, now.minus(LOCK_TIMEOUT)) == 1;
    }

    @Transactional
    public void unlock(ExternalSource source) {
        externalSyncStateRepository.releaseLock(source);
    }

    @Transactional
    public SyncWindow begin(ExternalSource source, LocalDate today) {
        ExternalSyncState state = getOrCreate(source);
        int callsUsed = state.callsUsedOn(today);
        if (state.hasOpenWindow()) {
            LocalDate from = Boolean.TRUE.equals(state.getWindowFull()) ? null : state.getProgressDate();
            int page = Math.max(1, state.getProgressPage() - 1);
            return new SyncWindow(state.getWindowFull(), from, state.getWindowEndDate(), page, true, callsUsed);
        }
        LocalDate from = parseCursor(state.getLastCursor());
        boolean full = from == null || from.isAfter(today) || from.isBefore(today.minusDays(MAX_INCREMENTAL_DAYS));
        state.openWindow(full, full ? null : from, today);
        return new SyncWindow(full, full ? null : from, today, 1, false, callsUsed);
    }

    @Transactional
    public void moveTo(ExternalSource source, LocalDate date, int page) {
        getOrCreate(source).moveTo(date, page);
    }

    @Transactional
    public void addCalls(ExternalSource source, LocalDate today, int calls) {
        if (calls > 0) {
            getOrCreate(source).addCalls(today, calls);
        }
    }

    @Transactional
    public void complete(ExternalSource source, LocalDate windowEnd, LocalDateTime succeededAt) {
        getOrCreate(source).completeWindow(windowEnd.format(CURSOR_FORMAT), succeededAt);
    }

    @Transactional
    public void pause(ExternalSource source, String reasonCode, String message) {
        getOrCreate(source).recordPause(reasonCode, message);
    }

    @Transactional
    public void fail(ExternalSource source, String errorCode, String error) {
        getOrCreate(source).recordFailure(errorCode, error);
    }

    private ExternalSyncState getOrCreate(ExternalSource source) {
        return externalSyncStateRepository.findByExternalSource(source)
                .orElseGet(() -> externalSyncStateRepository.save(
                        ExternalSyncState.builder().externalSource(source).build()));
    }

    static LocalDate parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(cursor, CURSOR_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
