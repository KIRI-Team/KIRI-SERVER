package com.kiri.server.domain.mission.entity;

import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.mission.enums.WalkingLevel;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalTime;

// 운영자가 편집하는 미션 본문 값
@Builder
public record MissionContent(
        String title,
        String description,
        Category category,
        MissionType missionType,
        IndoorOutdoor indoorOutdoor,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime openingTime,
        LocalTime closingTime,
        Integer price,
        Integer durationMinMinutes,
        Integer durationMaxMinutes,
        WalkingLevel walkingLevel,
        String imageUrl,
        String imageSource,
        String reservationUrl
) {
}
