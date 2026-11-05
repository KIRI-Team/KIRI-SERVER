package com.kiri.server.domain.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.kiri.server.domain.member.enums.FamilyRole;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

public class ReviewResDTO {

    @Builder
    @Schema(name = "ReviewCreateResponse", description = "리뷰 작성 응답")
    public record Create(
            Long reviewId,
            Long scheduleId
    ) {}

    @Builder
    public record ScheduleReviews(
            Long scheduleId,
            List<Item> reviews
    ) {}

    @Builder
    public record Item(
            Long reviewId,
            Writer writer,
            String content,
            Integer starRating,
            Boolean isRejoinIntended,
            List<String> imageUrls,
            LocalDateTime createdAt,
            Boolean isMine
    ) {}

    @Builder
    public record Writer(
            Long memberId,
            String nickname,
            FamilyRole familyRole
    ) {}
}
