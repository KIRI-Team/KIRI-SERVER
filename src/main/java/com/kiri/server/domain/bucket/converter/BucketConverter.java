package com.kiri.server.domain.bucket.converter;

import com.kiri.server.domain.bucket.dto.BucketResDTO;
import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.mission.entity.Mission;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class BucketConverter {

    public static BucketItem toBucketItem(Family family, Member member, Mission mission, LocalDateTime savedAt) {
        return BucketItem.builder()
                .family(family)
                .member(member)
                .mission(mission)
                .savedAt(savedAt)
                .build();
    }

    public static BucketResDTO.Saved toSaved(BucketItem item) {
        return BucketResDTO.Saved.builder()
                .bucketItemId(item.getId())
                .missionId(item.getMission().getId())
                .status(item.getStatus())
                .savedAt(item.getSavedAt())
                .build();
    }

    public static BucketResDTO.FamilyBuckets toFamilyBuckets(List<BucketResDTO.SaverCount> savers,
                                                             List<BucketResDTO.FamilyBucketItem> bucketItems,
                                                             Long nextCursor, boolean hasNext) {
        return BucketResDTO.FamilyBuckets.builder()
                .savers(savers)
                .bucketItems(bucketItems)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }

    public static BucketResDTO.SaverCount toSaverCount(Member member, long bucketCount) {
        return BucketResDTO.SaverCount.builder()
                .memberId(member.getId())
                .nickname(member.getNickname())
                .familyRole(member.getFamilyRole())
                .bucketCount(bucketCount)
                .build();
    }

    public static BucketResDTO.FamilyBucketItem toFamilyBucketItem(BucketItem item, boolean schedulable, boolean deletable,
                                                                   Long scheduleId, LocalDate scheduledDate) {
        return BucketResDTO.FamilyBucketItem.builder()
                .bucketItemId(item.getId())
                .status(item.getStatus())
                .savedAt(item.getSavedAt())
                .completedAt(item.getCompletedAt())
                .mission(toMissionSummary(item.getMission()))
                .saver(toSaver(item.getMember()))
                .schedulable(schedulable)
                .deletable(deletable)
                .scheduleId(scheduleId)
                .scheduledDate(scheduledDate)
                .build();
    }

    private static BucketResDTO.MissionSummary toMissionSummary(Mission mission) {
        return BucketResDTO.MissionSummary.builder()
                .missionId(mission.getId())
                .title(mission.getTitle())
                .imageUrl(mission.getImageUrl())
                .imageSource(mission.getImageUrl() == null ? null : mission.getImageSource())
                .status(mission.getStatus())
                .build();
    }

    private static BucketResDTO.Saver toSaver(Member member) {
        if (member == null) {
            return BucketResDTO.Saver.builder()
                    .withdrawn(true)
                    .build();
        }
        return BucketResDTO.Saver.builder()
                .memberId(member.getId())
                .nickname(member.getDisplayName())
                .familyRole(member.getFamilyRole())
                .withdrawn(member.isWithdrawn())
                .build();
    }
}
