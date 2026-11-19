package com.kiri.server.domain.external.repository;

import com.kiri.server.domain.external.entity.ExternalRecordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExternalRecordHistoryRepository extends JpaRepository<ExternalRecordHistory, Long> {
}
