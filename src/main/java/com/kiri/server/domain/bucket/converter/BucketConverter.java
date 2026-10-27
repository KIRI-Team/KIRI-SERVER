package com.kiri.server.domain.bucket.converter;

import com.kiri.server.domain.bucket.dto.BucketResDTO;
import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.mission.entity.Mission;

import java.time.LocalDateTime;

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
}
