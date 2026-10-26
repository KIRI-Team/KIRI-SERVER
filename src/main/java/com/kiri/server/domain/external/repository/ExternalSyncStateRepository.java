package com.kiri.server.domain.external.repository;

import com.kiri.server.domain.external.entity.ExternalSyncState;
import com.kiri.server.domain.external.enums.ExternalSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ExternalSyncStateRepository extends JpaRepository<ExternalSyncState, Long> {

    Optional<ExternalSyncState> findByExternalSource(ExternalSource externalSource);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ExternalSyncState s
               SET s.runningSince = :now
             WHERE s.externalSource = :source
               AND (s.runningSince IS NULL OR s.runningSince < :staleBefore)
            """)
    int acquireLock(@Param("source") ExternalSource source, @Param("now") LocalDateTime now,
                    @Param("staleBefore") LocalDateTime staleBefore);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ExternalSyncState s SET s.runningSince = NULL WHERE s.externalSource = :source")
    int releaseLock(@Param("source") ExternalSource source);
}
