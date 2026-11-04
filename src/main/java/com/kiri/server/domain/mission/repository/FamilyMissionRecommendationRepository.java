package com.kiri.server.domain.mission.repository;

import com.kiri.server.domain.mission.entity.FamilyMissionRecommendation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FamilyMissionRecommendationRepository extends JpaRepository<FamilyMissionRecommendation, Long> {

    @EntityGraph(attributePaths = "mission")
    Optional<FamilyMissionRecommendation> findFirstByFamilyIdOrderByRecommendationRankAsc(Long familyId);

    void deleteAllByFamilyId(Long familyId);
}
