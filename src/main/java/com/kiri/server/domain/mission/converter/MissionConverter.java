package com.kiri.server.domain.mission.converter;

import com.kiri.server.domain.mission.dto.MissionResDTO;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.entity.MissionChecklistItem;
import com.kiri.server.domain.mission.enums.ItemType;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.generator.dto.MissionDraft;
import com.kiri.server.domain.place.entity.Place;

import java.time.LocalTime;
import java.util.Comparator;
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

    public static Mission toMission(MissionDraft draft, Place place, MissionStatus status) {
        return Mission.builder()
                .place(place)
                .status(status)
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
                        .source(draft.source())
                        .title(draft.title())
                        .description(draft.description())
                        .displayOrder(draft.displayOrder())
                        .build())
                .toList();
    }

    public static MissionResDTO.Feed toFeed(List<MissionResDTO.FeedItem> missions, Long nextCursor, boolean hasNext) {
        return MissionResDTO.Feed.builder()
                .missions(missions)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }

    public static MissionResDTO.FeedItem toFeedItem(Mission mission, Double distance) {
        return MissionResDTO.FeedItem.builder()
                .missionId(mission.getId())
                .title(mission.getTitle())
                .imageUrl(mission.getImageUrl())
                .imageSource(imageSource(mission))
                .category(mission.getCategory())
                .missionType(mission.getMissionType())
                .placeName(mission.getPlace().getName())
                .startDate(mission.getStartDate())
                .endDate(mission.getEndDate())
                .price(mission.getPrice())
                .distance(distance)
                .build();
    }

    public static MissionResDTO.Detail toDetail(Mission mission, List<MissionChecklistItem> items, String sourceUrl,
                                                boolean bucketSaved) {
        Place place = mission.getPlace();
        boolean usePlaceHours = mission.getOpeningTime() == null && mission.getClosingTime() == null;
        LocalTime openingTime = usePlaceHours ? place.getOpeningTime() : mission.getOpeningTime();
        LocalTime closingTime = usePlaceHours ? place.getClosingTime() : mission.getClosingTime();

        return MissionResDTO.Detail.builder()
                .missionId(mission.getId())
                .title(mission.getTitle())
                .description(mission.getDescription())
                .category(mission.getCategory())
                .missionType(mission.getMissionType())
                .status(mission.getStatus())
                .indoorOutdoor(mission.getIndoorOutdoor())
                .imageUrl(mission.getImageUrl())
                .imageSource(imageSource(mission))
                .startDate(mission.getStartDate())
                .endDate(mission.getEndDate())
                .openingTime(openingTime)
                .closingTime(closingTime)
                .price(mission.getPrice())
                .durationMinMinutes(mission.getDurationMinMinutes())
                .durationMaxMinutes(mission.getDurationMaxMinutes())
                .estimatedStepsMin(mission.getEstimatedStepsMin())
                .estimatedStepsMax(mission.getEstimatedStepsMax())
                .sourceUrl(sourceUrl)
                .place(toPlaceInfo(place))
                .prepareItems(toChecklist(items, ItemType.PREPARE))
                .steps(toChecklist(items, ItemType.STEP))
                .bucketSaved(bucketSaved)
                .averageRating(null)
                .reviewCount(0)
                .build();
    }

    private static MissionResDTO.PlaceInfo toPlaceInfo(Place place) {
        return MissionResDTO.PlaceInfo.builder()
                .placeId(place.getId())
                .name(place.getName())
                .address(place.getAddress())
                .latitude(place.getLatitude().doubleValue())
                .longitude(place.getLongitude().doubleValue())
                .build();
    }

    private static List<MissionResDTO.ChecklistItem> toChecklist(List<MissionChecklistItem> items, ItemType itemType) {
        return items.stream()
                .filter(item -> item.getItemType() == itemType)
                .sorted(Comparator.comparing(MissionChecklistItem::getDisplayOrder))
                .map(item -> MissionResDTO.ChecklistItem.builder()
                        .title(item.getTitle())
                        .description(item.getDescription())
                        .build())
                .toList();
    }

    private static String imageSource(Mission mission) {
        return mission.getImageUrl() == null ? null : mission.getImageSource();
    }
}
