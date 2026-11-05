package com.kiri.server.domain.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.List;

public class ReviewReqDTO {

    @Schema(name = "ReviewCreateRequest", description = "리뷰 작성 요청")
    public record Create(
            @NotBlank(message = "리뷰 내용을 입력해주세요.")
            @Size(max = 500, message = "리뷰 내용은 500자 이내로 입력해주세요.")
            String content,

            @NotNull(message = "별점을 입력해주세요.")
            @Min(value = 1, message = "별점은 1점 이상이어야 합니다.")
            @Max(value = 5, message = "별점은 5점 이하이어야 합니다.")
            Integer starRating,

            @NotNull(message = "재참여 의사 여부를 입력해주세요.")
            Boolean isRejoinIntended,

            @NotEmpty(message = "사진은 최소 1장 이상 등록해주세요.")
            List<@NotBlank(message = "사진 키는 비어있을 수 없습니다.") String> imageKeys
    ) {}
}
