package com.kiri.server.domain.bucket.dto;

import com.kiri.server.domain.bucket.enums.BucketStatus;
import com.kiri.server.domain.member.enums.FamilyRole;
import com.kiri.server.domain.mission.enums.MissionStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class BucketResDTO {

    @Builder
    public record Saved(
            Long bucketItemId,
            Long missionId,
            BucketStatus status,
            LocalDateTime savedAt
    ) {
    }

    @Builder
    public record FamilyBuckets(
            List<SaverCount> savers,
            List<FamilyBucketItem> bucketItems,
            Long nextCursor,
            Boolean hasNext
    ) {
    }

    @Builder
    public record SaverCount(
            Long memberId,
            String nickname,
            FamilyRole familyRole,
            Long bucketCount
    ) {
    }

    @Builder
    public record FamilyBucketItem(
            Long bucketItemId,
            BucketStatus status,
            LocalDateTime savedAt,
            LocalDateTime completedAt,
            MissionSummary mission,
            Saver saver,
            Boolean schedulable,
            Boolean deletable,
            Long scheduleId,
            LocalDate scheduledDate
    ) {
    }

    @Builder
    public record MissionSummary(
            Long missionId,
            String title,
            String imageUrl,
            String imageSource,
            MissionStatus status
    ) {
    }

    @Builder
    public record Saver(
            Long memberId,
            String nickname,
            FamilyRole familyRole,
            Boolean withdrawn
    ) {
    }
}
