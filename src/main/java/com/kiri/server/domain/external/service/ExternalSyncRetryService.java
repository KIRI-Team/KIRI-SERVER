package com.kiri.server.domain.external.service;

import com.kiri.server.domain.external.entity.ExternalSyncRetry;
import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.repository.ExternalSyncRetryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExternalSyncRetryService {

    public static final int MAX_ATTEMPTS = 3;

    private final ExternalSyncRetryRepository externalSyncRetryRepository;

    @Transactional(readOnly = true)
    public List<ExternalSyncRetry> pending(ExternalSource source) {
        return externalSyncRetryRepository.findTop50ByExternalSourceAndAttemptCountLessThanOrderByIdAsc(source, MAX_ATTEMPTS);
    }

    @Transactional
    public void recordFailure(ExternalSource source, String externalId, String rawItem, String error) {
        ExternalSyncRetry retry = externalSyncRetryRepository.findByExternalSourceAndExternalId(source, externalId)
                .orElseGet(() -> externalSyncRetryRepository.save(ExternalSyncRetry.builder()
                        .externalSource(source)
                        .externalId(externalId)
                        .rawItem(rawItem)
                        .build()));
        retry.recordFailure(rawItem, error);
    }

    @Transactional
    public void resolve(ExternalSource source, String externalId) {
        externalSyncRetryRepository.findByExternalSourceAndExternalId(source, externalId)
                .ifPresent(externalSyncRetryRepository::delete);
    }
}
