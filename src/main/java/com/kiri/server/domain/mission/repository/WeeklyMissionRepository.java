package com.kiri.server.domain.mission.repository;

import com.kiri.server.domain.mission.entity.WeeklyMission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface WeeklyMissionRepository extends JpaRepository<WeeklyMission, Long> {

    Optional<WeeklyMission> findByFamily_IdAndStartDate(Long familyId, LocalDate startDate);
}
