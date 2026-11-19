package com.kiri.server.domain.external.repository;

import com.kiri.server.domain.external.entity.ExternalRecord;
import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.enums.ProcessStatus;
import com.kiri.server.domain.mission.entity.Mission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExternalRecordRepository extends JpaRepository<ExternalRecord, Long> {

    Optional<ExternalRecord> findByExternalSourceAndExternalId(ExternalSource externalSource, String externalId);

    List<ExternalRecord> findAllByExternalSourceAndProcessStatusNot(ExternalSource externalSource,
                                                                    ProcessStatus processStatus);

    Optional<ExternalRecord> findFirstByMissionAndSourceUrlIsNotNullOrderByIdAsc(Mission mission);
}
