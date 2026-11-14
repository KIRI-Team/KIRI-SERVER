package com.kiri.server.domain.mission.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.kiri.server.domain.bucket.enums.BucketSaveState;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.mission.enums.WalkingLevel;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class MissionResDTO {

    @Builder
    public record Feed(
            List<FeedItem> missions,
            Long nextCursor,
            Boolean hasNext
    ) {
    }

    @Builder
    public record FeedItem(
            Long missionId,
            String title,
            String imageUrl,
            String imageSource,
            Category category,
            MissionType missionType,
            String placeName,
            LocalDate startDate,
            LocalDate endDate,
            Integer price,
            Double distance,
            // 끼리 픽으로 상단에 노출된 카드면 채워진다. 버킷 저장 시 kiriPickId를 함께 보내 주세요.
            KiriPickInfo kiriPick
    ) {
    }

    @Builder
    public record NearbyItem(
            Long missionId,
            String placeName,
            String region,
            String description,
            String imageUrl,
            Integer price,
            Double distance
    ) {
    }

    @Builder
    public record KiriPickInfo(
            Long kiriPickId,
            String headline,
            String description,
            String imageUrl
    ) {
    }

    @Builder
    public record Detail(
            Long missionId,
            String title,
            String description,
            Category category,
            MissionType missionType,
            MissionStatus status,
            IndoorOutdoor indoorOutdoor,
            String imageUrl,
            String imageSource,
            LocalDate startDate,
            LocalDate endDate,
            @JsonFormat(pattern = "HH:mm")
            LocalTime openingTime,
            @JsonFormat(pattern = "HH:mm")
            LocalTime closingTime,
            Integer price,
            Integer durationMinMinutes,
            Integer durationMaxMinutes,
            WalkingLevel walkingLevel,
            String sourceUrl,
            PlaceInfo place,
            List<ChecklistItem> prepareItems,
            List<ChecklistItem> steps,
            Boolean bucketSaved,
            BucketSaveState bucketState,
            Double averageRating,
            Integer reviewCount
    ) {
    }

    @Builder
    public record PlaceInfo(
            Long placeId,
            String name,
            String address,
            Double latitude,
            Double longitude
    ) {
    }

    @Builder
    public record ChecklistItem(
            String title,
            String description
    ) {
    }

    @Builder
    public record Recommendation(
            Long missionId,
            String title,
            String description,
            Category category,
            String imageUrl,
            String recommendationReason,
            BigDecimal score
    ) {}
}
