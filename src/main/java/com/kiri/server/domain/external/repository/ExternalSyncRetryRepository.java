package com.kiri.server.domain.external.repository;

import com.kiri.server.domain.external.entity.ExternalSyncRetry;
import com.kiri.server.domain.external.enums.ExternalSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExternalSyncRetryRepository extends JpaRepository<ExternalSyncRetry, Long> {

    Optional<ExternalSyncRetry> findByExternalSourceAndExternalId(ExternalSource externalSource, String externalId);

    List<ExternalSyncRetry> findTop50ByExternalSourceAndAttemptCountLessThanOrderByIdAsc(
            ExternalSource externalSource, int maxAttempts);
}
