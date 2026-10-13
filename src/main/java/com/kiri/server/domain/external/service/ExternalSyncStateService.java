package com.kiri.server.domain.external.service;

import com.kiri.server.domain.external.entity.ExternalSyncState;
import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.repository.ExternalSyncStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ExternalSyncStateService {

    private final ExternalSyncStateRepository externalSyncStateRepository;

    @Transactional(readOnly = true)
    public String getCursor(ExternalSource source) {
        return externalSyncStateRepository.findByExternalSource(source)
                .map(ExternalSyncState::getLastCursor)
                .orElse(null);
    }

    @Transactional
    public void recordSuccess(ExternalSource source, String nextCursor, LocalDateTime succeededAt) {
        getOrCreate(source).recordSuccess(nextCursor, succeededAt);
    }

    @Transactional
    public void recordFailure(ExternalSource source, String error) {
        getOrCreate(source).recordFailure(error);
    }

    private ExternalSyncState getOrCreate(ExternalSource source) {
        return externalSyncStateRepository.findByExternalSource(source)
                .orElseGet(() -> externalSyncStateRepository.save(
                        ExternalSyncState.builder().externalSource(source).build()));
    }
}
