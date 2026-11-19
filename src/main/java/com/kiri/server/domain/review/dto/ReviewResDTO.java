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
            @Schema(description = "현재 대표사진의 reviewPhotoId (지정하지 않았으면 기본값, 사진이 없으면 null)")
            Long representativePhotoId,
            List<Item> reviews
    ) {}

    @Builder
    @Schema(name = "RepresentativePhotoChangeResponse", description = "대표사진 변경 응답")
    public record RepresentativePhoto(
            Long scheduleId,
            Long representativePhotoId
    ) {}

    @Builder
    public record Item(
            Long reviewId,
            Writer writer,
            String content,
            Integer starRating,
            Boolean isRejoinIntended,
            List<Photo> photos,
            LocalDateTime createdAt,
            Boolean isMine
    ) {}

    @Builder
    @Schema(name = "ReviewPhotoResponse", description = "리뷰 사진 (표시 순서대로)")
    public record Photo(
            Long reviewPhotoId,
            @Schema(description = "리뷰 수정 시 유지할 사진은 이 키를 imageKeys에 그대로 전달")
            String imageKey,
            String imageUrl
    ) {}

    @Builder
    public record Writer(
            Long memberId,
            String nickname,
            FamilyRole familyRole
    ) {}
}
