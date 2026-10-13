package com.kiri.server.domain.mission.converter;

import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.entity.MissionChecklistItem;
import com.kiri.server.domain.mission.generator.dto.MissionDraft;
import com.kiri.server.domain.place.entity.Place;

import java.util.List;

public class MissionConverter {

    public static Place toPlace(MissionDraft.PlaceDraft draft) {
        return Place.builder()
                .name(draft.name())
                .placeType(draft.placeType())
                .regionCode(draft.regionCode())
                .latitude(draft.latitude())
                .longitude(draft.longitude())
                .address(draft.address())
                .openingTime(draft.openingTime())
                .closingTime(draft.closingTime())
                .imageUrl(draft.imageUrl())
                .imageSource(draft.imageSource())
                .build();
    }

    public static Mission toMission(MissionDraft draft, Place place) {
        return Mission.builder()
                .place(place)
                .title(draft.title())
                .description(draft.description())
                .category(draft.category())
                .missionType(draft.missionType())
                .imageUrl(draft.imageUrl())
                .imageSource(draft.imageSource())
                .durationMinMinutes(draft.durationMinMinutes())
                .durationMaxMinutes(draft.durationMaxMinutes())
                .price(draft.price())
                .estimatedStepsMin(draft.estimatedStepsMin())
                .estimatedStepsMax(draft.estimatedStepsMax())
                .indoorOutdoor(draft.indoorOutdoor())
                .startDate(draft.startDate())
                .endDate(draft.endDate())
                .openingTime(draft.openingTime())
                .closingTime(draft.closingTime())
                .build();
    }

    public static List<MissionChecklistItem> toChecklistItems(Mission mission, List<MissionDraft.ChecklistDraft> drafts) {
        return drafts.stream()
                .map(draft -> MissionChecklistItem.builder()
                        .mission(mission)
                        .itemType(draft.itemType())
                        .title(draft.title())
                        .description(draft.description())
                        .displayOrder(draft.displayOrder())
                        .build())
                .toList();
    }
}
