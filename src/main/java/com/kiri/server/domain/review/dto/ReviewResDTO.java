package com.kiri.server.domain.review.dto;

import lombok.Builder;

public class ReviewResDTO {

    @Builder
    public record Create(
            Long reviewId,
            Long scheduleId
    ) {}
}
