package com.kiri.server.domain.pick.entity;

import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.pick.enums.PickType;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 운영자가 고른 공통 큐레이션. 가족별 개인화 추천·주간 미션과는 별개다.
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "kiri_pick")
public class KiriPick extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "kiri_pick_id")
    private Long id;

    // 대표 미션. 장소에 미션이 여러 개면 운영자가 하나를 고른다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @Column(name = "headline", length = 100, nullable = false)
    private String headline;

    @Column(name = "description", length = 500)
    private String description;

    // 비어 있으면 미션 이미지를 쓴다.
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "pick_type", length = 20, nullable = false)
    private PickType pickType = PickType.CURATION;

    @Column(name = "partner_name", length = 100)
    private String partnerName;

    @Column(name = "created_by")
    private Long createdBy;

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }

    // 실제로 앱에 보이는지. 편성이 켜져 있어도 미션이 공개 상태가 아니면 노출하지 않는다.
    public boolean isExposedAt(LocalDateTime now) {
        return isActive() && !now.isBefore(startAt) && now.isBefore(endAt)
                && mission.getStatus() == MissionStatus.ACTIVE;
    }

    public void update(Mission mission, String headline, String description, String imageUrl, Integer displayOrder,
                       LocalDateTime startAt, LocalDateTime endAt, boolean active, PickType pickType, String partnerName) {
        this.mission = mission;
        this.headline = headline;
        this.description = description;
        this.imageUrl = imageUrl;
        this.displayOrder = displayOrder;
        this.startAt = startAt;
        this.endAt = endAt;
        this.active = active;
        this.pickType = pickType;
        this.partnerName = partnerName;
    }

    public void reorder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public String displayImageUrl() {
        return imageUrl != null ? imageUrl : mission.getImageUrl();
    }
}
