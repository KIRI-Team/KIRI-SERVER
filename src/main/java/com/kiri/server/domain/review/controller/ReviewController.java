package com.kiri.server.domain.review.controller;

import com.kiri.server.domain.review.controller.docs.ReviewControllerDocs;
import com.kiri.server.domain.review.dto.ReviewReqDTO;
import com.kiri.server.domain.review.dto.ReviewResDTO;
import com.kiri.server.domain.review.exception.code.ReviewSuccessCode;
import com.kiri.server.domain.review.servide.ReviewService;
import com.kiri.server.global.apiPayload.ApiResponse;
import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/schedules")
public class ReviewController implements ReviewControllerDocs {

    private final ReviewService reviewService;

    @Override
    @PostMapping("/{scheduleId}/reviews")
    public ApiResponse<ReviewResDTO.Create> createReview(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long scheduleId,
            @Valid @RequestBody ReviewReqDTO.Create dto
    ) {
        BaseSuccessCode code = ReviewSuccessCode.REVIEW_CREATED;
        return ApiResponse.onSuccess(code, reviewService.createReview(memberId, scheduleId, dto));
    }
}
