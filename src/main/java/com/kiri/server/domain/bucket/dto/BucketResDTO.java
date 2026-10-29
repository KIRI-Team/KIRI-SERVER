package com.kiri.server.domain.bucket.dto;

import com.kiri.server.domain.bucket.enums.BucketStatus;
import lombok.Builder;

import java.time.LocalDateTime;

public class BucketResDTO {

    @Builder
    public record Saved(
            Long bucketItemId,
            Long missionId,
            BucketStatus status,
            LocalDateTime savedAt
    ) {
    }
}
