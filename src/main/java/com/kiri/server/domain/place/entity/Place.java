package com.kiri.server.domain.place.entity;

import com.kiri.server.domain.mission.generator.dto.MissionDraft;
import com.kiri.server.domain.place.enums.PlaceType;
import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.kiri.server.domain.mission.enums.LockedField;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "place")
public class Place extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_id")
    private Long id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "place_type", length = 20, nullable = false)
    private PlaceType placeType;

    @Column(name = "region_code", length = 10)
    private String regionCode;

    @Column(name = "latitude", precision = 10, scale = 7, nullable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7, nullable = false)
    private BigDecimal longitude;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "opening_time")
    private LocalTime openingTime;

    @Column(name = "closing_time")
    private LocalTime closingTime;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "image_source", length = 255)
    private String imageSource;

    public void fillMissingFrom(MissionDraft.PlaceDraft draft) {
        if (regionCode == null) {
            this.regionCode = draft.regionCode();
        }
        if (address == null || address.isBlank()) {
            this.address = draft.address();
        }
        if (openingTime == null && closingTime == null) {
            this.openingTime = draft.openingTime();
            this.closingTime = draft.closingTime();
        }
    }

    public void refreshImage(String imageUrl, String imageSource) {
        if (isLocked(LockedField.IMAGE)) {
            return;
        }
        this.imageUrl = imageUrl;
        this.imageSource = imageUrl == null ? null : imageSource;
    }

    // 운영자가 고친 필드(LockedField 이름). 외부 재수집이 덮어쓰지 않는다.
    @Builder.Default
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "locked_fields", columnDefinition = "text[]", nullable = false)
    private List<String> lockedFields = new ArrayList<>();

    public static Place create(PlaceContent content) {
        Place place = Place.builder().name(content.name()).placeType(content.placeType())
                .latitude(content.latitude()).longitude(content.longitude()).build();
        place.applyContent(content);
        return place;
    }

    // 운영자가 넣은 값을 반영하고 바뀐 필드를 잠근다.
    public Set<LockedField> applyAdminContent(PlaceContent content) {
        Set<LockedField> changed = EnumSet.noneOf(LockedField.class);
        if (!Objects.equals(name, content.name())) changed.add(LockedField.NAME);
        if (placeType != content.placeType()) changed.add(LockedField.PLACE_TYPE);
        if (!Objects.equals(regionCode, content.regionCode())) changed.add(LockedField.REGION);
        if (!sameNumber(latitude, content.latitude()) || !sameNumber(longitude, content.longitude())) {
            changed.add(LockedField.COORDINATE);
        }
        if (!Objects.equals(address, content.address())) changed.add(LockedField.ADDRESS);
        if (!Objects.equals(openingTime, content.openingTime())) changed.add(LockedField.OPENING_TIME);
        if (!Objects.equals(closingTime, content.closingTime())) changed.add(LockedField.CLOSING_TIME);
        if (!Objects.equals(imageUrl, content.imageUrl())) changed.add(LockedField.IMAGE);
        applyContent(content);
        List<String> next = new ArrayList<>(lockedFields == null ? List.of() : lockedFields);
        changed.stream().map(Enum::name).filter(n -> !next.contains(n)).forEach(next::add);
        this.lockedFields = next;
        return changed;
    }

    public boolean isLocked(LockedField field) {
        return lockedFields != null && lockedFields.contains(field.name());
    }

    private void applyContent(PlaceContent c) {
        this.name = c.name();
        this.placeType = c.placeType();
        this.regionCode = c.regionCode();
        this.latitude = c.latitude();
        this.longitude = c.longitude();
        this.address = c.address();
        this.openingTime = c.openingTime();
        this.closingTime = c.closingTime();
        this.imageUrl = c.imageUrl();
        this.imageSource = c.imageUrl() == null ? null : c.imageSource();
    }

    private static boolean sameNumber(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }
}
