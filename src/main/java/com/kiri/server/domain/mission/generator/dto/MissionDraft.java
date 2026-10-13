package com.kiri.server.domain.mission.generator.dto;

import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.mission.enums.ItemType;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.place.enums.PlaceType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Builder
public record MissionDraft(
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
        Integer estimatedStepsMin,
        Integer estimatedStepsMax,
        String imageUrl,
        String imageSource,
        PlaceDraft place,
        List<ChecklistDraft> checklist
) {

    public MissionDraft {
        checklist = checklist == null ? List.of() : List.copyOf(checklist);
    }

    public boolean isPeriodOver(LocalDate today) {
        return missionType == MissionType.PERIOD && endDate != null && endDate.isBefore(today);
    }

    @Builder
    public record PlaceDraft(
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

    public record ChecklistDraft(ItemType itemType, String title, String description, int displayOrder) {
    }
}
