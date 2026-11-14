package com.kiri.server.domain.mission.repository;

import com.kiri.server.domain.mission.entity.FamilyMissionRecommendation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FamilyMissionRecommendationRepository extends JpaRepository<FamilyMissionRecommendation, Long> {

    Optional<FamilyMissionRecommendation> findFirstByFamilyIdAndStartedAtLessThanEqualAndEndedAtGreaterThanOrderByStartedAtDesc(Long familyId, LocalDateTime startedAt, LocalDateTime endedAt);

    boolean existsByFamilyIdAndStartedAt(Long familyId, LocalDateTime startedAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT fmr
            FROM FamilyMissionRecommendation fmr
            WHERE fmr.family.id = :familyId
              AND fmr.startedAt <= :now
              AND fmr.endedAt > :now
            ORDER BY fmr.startedAt DESC
            """)
    Optional<FamilyMissionRecommendation> findCurrentRecommendationForUpdate(@Param("familyId") Long familyId, @Param("now") LocalDateTime now);
}
