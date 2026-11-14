package com.kiri.server.domain.mission.converter;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.mission.entity.FamilyMissionRecommendation;
import com.kiri.server.domain.mission.entity.Mission;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class FamilyMissionRecommendationConverter {

    public static FamilyMissionRecommendation toFamilyMissionRecommendation(Family family, Mission mission, BigDecimal recommendationScore, Integer recommendationRank, LocalDateTime startedAt, LocalDateTime endedAt) {
        return FamilyMissionRecommendation.builder()
                .family(family)
                .mission(mission)
                .recommendationScore(recommendationScore)
                .recommendationRank(recommendationRank)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .build();
    }
}
