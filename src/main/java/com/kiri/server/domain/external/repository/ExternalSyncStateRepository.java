package com.kiri.server.domain.external.repository;

import com.kiri.server.domain.external.entity.ExternalSyncState;
import com.kiri.server.domain.external.enums.ExternalSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExternalSyncStateRepository extends JpaRepository<ExternalSyncState, Long> {

    Optional<ExternalSyncState> findByExternalSource(ExternalSource externalSource);
}
