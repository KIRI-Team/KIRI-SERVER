package com.kiri.server.domain.place.entity;

import com.kiri.server.domain.place.enums.PlaceType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalTime;

// 운영자가 편집하는 장소 공통 정보
@Builder
public record PlaceContent(
        String name,
        PlaceType placeType,
        String regionCode,
        BigDecimal latitude,
        BigDecimal longitude,
        String address,
        LocalTime openingTime,
        LocalTime closingTime,
        String imageUrl,
        String imageSource
) {
}
