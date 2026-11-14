package com.kiri.server.domain.mission.entity;

import com.kiri.server.domain.mission.enums.CancelSource;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.mission.enums.LockedField;
import com.kiri.server.domain.mission.enums.MissionOrigin;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.mission.enums.WalkingLevel;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "walking_level", length = 20)
    private WalkingLevel walkingLevel;

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

    // 운영자가 고친 필드(LockedField 이름). 외부 재수집(updateContent)이 덮어쓰지 않는다.
    @Builder.Default
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "locked_fields", columnDefinition = "text[]", nullable = false)
    private List<String> lockedFields = new ArrayList<>();

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "origin", length = 20, nullable = false)
    private MissionOrigin origin = MissionOrigin.EXTERNAL;

    @Column(name = "reservation_url", length = 500)
    private String reservationUrl;

    public void updateContent(MissionDraft draft, Place place) {
        if (!isLocked(LockedField.PLACE)) {
            this.place = place;
        }
        if (!isLocked(LockedField.TITLE)) {
            this.title = draft.title();
        }
        if (!isLocked(LockedField.DESCRIPTION)) {
            this.description = draft.description();
        }
        if (!isLocked(LockedField.CATEGORY)) {
            this.category = draft.category();
        }
        if (!isLocked(LockedField.MISSION_TYPE)) {
            this.missionType = draft.missionType();
        }
        if (!isLocked(LockedField.IMAGE)) {
            this.imageUrl = draft.imageUrl();
            this.imageSource = draft.imageSource();
        }
        if (!isLocked(LockedField.DURATION)) {
            this.durationMinMinutes = draft.durationMinMinutes();
            this.durationMaxMinutes = draft.durationMaxMinutes();
        }
        if (!isLocked(LockedField.PRICE)) {
            this.price = draft.price();
        }
        if (!isLocked(LockedField.WALKING_LEVEL)) {
            this.walkingLevel = draft.walkingLevel();
        }
        if (!isLocked(LockedField.INDOOR_OUTDOOR)) {
            this.indoorOutdoor = draft.indoorOutdoor();
        }
        if (!isLocked(LockedField.PERIOD)) {
            this.startDate = draft.startDate();
            this.endDate = draft.endDate();
        }
        if (!isLocked(LockedField.OPENING_TIME)) {
            this.openingTime = draft.openingTime();
        }
        if (!isLocked(LockedField.CLOSING_TIME)) {
            this.closingTime = draft.closingTime();
        }
    }

    // ===== 운영자 편집 =====

    public static Mission createManual(MissionContent content, Place place) {
        Mission mission = Mission.builder()
                .place(place)
                .status(MissionStatus.DRAFT)
                .origin(MissionOrigin.MANUAL)
                .title(content.title())
                .description(content.description())
                .category(content.category())
                .missionType(content.missionType())
                .build();
        mission.applyContent(content);
        return mission;
    }

    public static Mission createApproved(MissionContent content, Place place) {
        Mission mission = Mission.builder()
                .place(place)
                .status(MissionStatus.ACTIVE)
                .origin(MissionOrigin.EXTERNAL)
                .title(content.title())
                .description(content.description())
                .category(content.category())
                .missionType(content.missionType())
                .build();
        mission.applyContent(content);
        return mission;
    }

    // 운영자가 넣은 값을 반영하고, 실제로 바뀐 필드를 돌려준다.
    public Set<LockedField> applyAdminContent(MissionContent content, Place newPlace) {
        Set<LockedField> changed = diff(content);
        if (newPlace != null && !Objects.equals(place == null ? null : place.getId(), newPlace.getId())) {
            changed.add(LockedField.PLACE);
            this.place = newPlace;
        }
        applyContent(content);
        return changed;
    }

    public Set<LockedField> diff(MissionContent c) {
        Set<LockedField> changed = EnumSet.noneOf(LockedField.class);
        if (!Objects.equals(title, c.title())) changed.add(LockedField.TITLE);
        if (!Objects.equals(description, c.description())) changed.add(LockedField.DESCRIPTION);
        if (category != c.category()) changed.add(LockedField.CATEGORY);
        if (missionType != c.missionType()) changed.add(LockedField.MISSION_TYPE);
        if (indoorOutdoor != c.indoorOutdoor()) changed.add(LockedField.INDOOR_OUTDOOR);
        if (!Objects.equals(startDate, c.startDate()) || !Objects.equals(endDate, c.endDate())) changed.add(LockedField.PERIOD);
        if (!Objects.equals(openingTime, c.openingTime())) changed.add(LockedField.OPENING_TIME);
        if (!Objects.equals(closingTime, c.closingTime())) changed.add(LockedField.CLOSING_TIME);
        if (!Objects.equals(price, c.price())) changed.add(LockedField.PRICE);
        if (!Objects.equals(durationMinMinutes, c.durationMinMinutes())
                || !Objects.equals(durationMaxMinutes, c.durationMaxMinutes())) changed.add(LockedField.DURATION);
        if (walkingLevel != c.walkingLevel()) changed.add(LockedField.WALKING_LEVEL);
        if (!Objects.equals(imageUrl, c.imageUrl())) changed.add(LockedField.IMAGE);
        if (!Objects.equals(reservationUrl, c.reservationUrl())) changed.add(LockedField.RESERVATION_URL);
        return changed;
    }

    private void applyContent(MissionContent c) {
        this.title = c.title();
        this.description = c.description();
        this.category = c.category();
        this.missionType = c.missionType();
        this.indoorOutdoor = c.indoorOutdoor();
        this.startDate = c.startDate();
        this.endDate = c.endDate();
        this.openingTime = c.openingTime();
        this.closingTime = c.closingTime();
        this.price = c.price();
        this.durationMinMinutes = c.durationMinMinutes();
        this.durationMaxMinutes = c.durationMaxMinutes();
        this.walkingLevel = c.walkingLevel();
        this.imageUrl = c.imageUrl();
        this.imageSource = c.imageUrl() == null ? null : c.imageSource();
        this.reservationUrl = c.reservationUrl();
    }

    public boolean isLocked(LockedField field) {
        if (field == LockedField.TITLE && Boolean.TRUE.equals(titleEdited)) {
            return true;
        }
        if (field == LockedField.CHECKLIST && Boolean.TRUE.equals(checklistEdited)) {
            return true;
        }
        return lockedFields != null && lockedFields.contains(field.name());
    }

    public void lock(Collection<LockedField> fields) {
        List<String> next = new ArrayList<>(lockedFields == null ? List.of() : lockedFields);
        fields.stream().map(Enum::name).filter(name -> !next.contains(name)).forEach(next::add);
        this.lockedFields = next;
    }

    public void unlock(LockedField field) {
        List<String> next = new ArrayList<>(lockedFields == null ? List.of() : lockedFields);
        next.remove(field.name());
        this.lockedFields = next;
        if (field == LockedField.TITLE) {
            this.titleEdited = false;
        }
        if (field == LockedField.CHECKLIST) {
            this.checklistEdited = false;
        }
    }

    // 초안 공개. 기간이 이미 지났으면 바로 종료로 간다.
    public void publishDraft(LocalDate today) {
        if (status == MissionStatus.DRAFT) {
            this.status = MissionStatus.ACTIVE;
        }
        endIfPeriodOver(today);
    }

    // 공개 대기(CHECK_REQUIRED) 미션을 운영자가 공개한다.
    public void publishFromCheck(LocalDate today) {
        restoreFromCheck();
        endIfPeriodOver(today);
    }

    public void hide() {
        if (status != MissionStatus.CANCELLED) {
            this.status = MissionStatus.HIDDEN;
        }
    }

    public void restore(LocalDate today) {
        if (status == MissionStatus.HIDDEN) {
            this.status = MissionStatus.ACTIVE;
            endIfPeriodOver(today);
        }
    }

    public void reactivate(LocalDate today) {
        if (status == MissionStatus.CHECK_REQUIRED || status == MissionStatus.ENDED) {
            this.status = MissionStatus.ACTIVE;
        }
        endIfPeriodOver(today);
    }

    public boolean isChecklistLocked() {
        return isLocked(LockedField.CHECKLIST);
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
