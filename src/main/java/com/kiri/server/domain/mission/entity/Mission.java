package com.kiri.server.domain.mission.entity;

import com.kiri.server.domain.mission.enums.CancelSource;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.mission.generator.dto.MissionDraft;
import com.kiri.server.domain.place.entity.Place;
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

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "mission")
public class Mission extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mission_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(name = "title", length = 100, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20, nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "mission_type", length = 20, nullable = false)
    private MissionType missionType;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private MissionStatus status = MissionStatus.ACTIVE;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "image_source", length = 255)
    private String imageSource;

    @Column(name = "duration_min_minutes")
    private Integer durationMinMinutes;

    @Column(name = "duration_max_minutes")
    private Integer durationMaxMinutes;

    @Column(name = "price")
    private Integer price;

    @Column(name = "estimated_steps_min")
    private Integer estimatedStepsMin;

    @Column(name = "estimated_steps_max")
    private Integer estimatedStepsMax;

    @Enumerated(EnumType.STRING)
    @Column(name = "indoor_outdoor", length = 20)
    private IndoorOutdoor indoorOutdoor;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "recommendation_reason", length = 255)
    private String recommendationReason;

    @Column(name = "opening_time")
    private LocalTime openingTime;

    @Column(name = "closing_time")
    private LocalTime closingTime;

    @Builder.Default
    @Column(name = "title_edited", nullable = false)
    private Boolean titleEdited = false;

    @Builder.Default
    @Column(name = "checklist_edited", nullable = false)
    private Boolean checklistEdited = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_source", length = 20)
    private CancelSource cancelSource;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    public void updateContent(MissionDraft draft, Place place) {
        this.place = place;
        if (!Boolean.TRUE.equals(titleEdited)) {
            this.title = draft.title();
        }
        this.description = draft.description();
        this.category = draft.category();
        this.missionType = draft.missionType();
        this.imageUrl = draft.imageUrl();
        this.imageSource = draft.imageSource();
        this.durationMinMinutes = draft.durationMinMinutes();
        this.durationMaxMinutes = draft.durationMaxMinutes();
        this.price = draft.price();
        this.estimatedStepsMin = draft.estimatedStepsMin();
        this.estimatedStepsMax = draft.estimatedStepsMax();
        this.indoorOutdoor = draft.indoorOutdoor();
        this.startDate = draft.startDate();
        this.endDate = draft.endDate();
        this.openingTime = draft.openingTime();
        this.closingTime = draft.closingTime();
    }

    public void reactivate(LocalDate today) {
        if (status == MissionStatus.CHECK_REQUIRED || status == MissionStatus.ENDED) {
            this.status = MissionStatus.ACTIVE;
        }
        endIfPeriodOver(today);
    }

    public boolean isChecklistLocked() {
        return Boolean.TRUE.equals(checklistEdited);
    }

    public void cancelByExternal(String reason) {
        if (status == MissionStatus.ACTIVE || status == MissionStatus.CHECK_REQUIRED || status == MissionStatus.ENDED) {
            this.status = MissionStatus.CANCELLED;
            this.cancelSource = CancelSource.EXTERNAL;
            this.cancelReason = reason;
        }
    }

    public void requireCheck() {
        if (status == MissionStatus.ACTIVE) {
            this.status = MissionStatus.CHECK_REQUIRED;
        }
    }

    public void restoreFromCheck() {
        if (status == MissionStatus.CHECK_REQUIRED) {
            this.status = MissionStatus.ACTIVE;
        }
    }

    public boolean isPeriodOver(LocalDate today) {
        return missionType == MissionType.PERIOD && endDate != null && endDate.isBefore(today);
    }

    public void endIfPeriodOver(LocalDate today) {
        if (status == MissionStatus.ACTIVE && isPeriodOver(today)) {
            this.status = MissionStatus.ENDED;
        }
    }
}
