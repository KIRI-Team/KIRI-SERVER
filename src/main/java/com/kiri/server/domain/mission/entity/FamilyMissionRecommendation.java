package com.kiri.server.domain.mission.entity;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "family_mission_recommendation",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_family_mission_recommendation_family_mission", columnNames = {"family_id", "mission_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FamilyMissionRecommendation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "family_mission_recommendation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @Column(name = "recommendation_score", nullable = false)
    private BigDecimal recommendationScore;

    @Column(name = "recommendation_rank", nullable = false)
    private Integer recommendationRank;
}
