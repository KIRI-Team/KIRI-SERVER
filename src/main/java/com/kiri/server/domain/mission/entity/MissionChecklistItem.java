package com.kiri.server.domain.mission.entity;

import com.kiri.server.domain.mission.enums.ChecklistSource;
import com.kiri.server.domain.mission.enums.ItemType;
import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "mission_checklist_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mission_checklist_item_order",
                columnNames = {"mission_id", "item_type", "display_order"}
        )
)
public class MissionChecklistItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mission_checklist_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", length = 20, nullable = false)
    private ItemType itemType;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20, nullable = false)
    private ChecklistSource source = ChecklistSource.ORIGIN;

    @Column(name = "title", length = 100, nullable = false)
    private String title;

    @Column(name = "description", length = 255)
    private String description;

    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 1;
}
