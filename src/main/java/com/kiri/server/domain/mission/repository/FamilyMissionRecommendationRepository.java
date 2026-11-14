package com.kiri.server.domain.mission.repository;

import com.kiri.server.domain.mission.entity.FamilyMissionRecommendation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FamilyMissionRecommendationRepository extends JpaRepository<FamilyMissionRecommendation, Long> {

    Optional<FamilyMissionRecommendation> findFirstByFamilyIdAndStartedAtLessThanEqualAndEndedAtGreaterThanOrderByStartedAtDesc(Long familyId, LocalDateTime startedAt, LocalDateTime endedAt);
    boolean existsByFamilyIdAndStartedAt(Long familyId, LocalDateTime startedAt);
}
