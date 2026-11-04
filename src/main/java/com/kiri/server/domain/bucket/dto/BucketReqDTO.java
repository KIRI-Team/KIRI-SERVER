package com.kiri.server.domain.bucket.dto;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Builder;

public class BucketReqDTO {

    @Builder
    public record FamilyBucketCondition(
            @Parameter(description = "저장자 회원 ID. 생략하면 전체", example = "2")
            Long memberId,

            @Parameter(description = "이전 응답의 nextCursor. 첫 조회는 생략", example = "42")
            Long cursor,

            @Parameter(description = "조회 개수. 1~50, 기본 20", example = "20")
            @Min(value = 1, message = "size는 1 이상 50 이하여야 합니다.")
            @Max(value = 50, message = "size는 1 이상 50 이하여야 합니다.")
            Integer size
    ) {
    }
}
