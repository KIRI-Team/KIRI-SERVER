package com.kiri.server.domain.review.controller;

import com.kiri.server.domain.review.controller.docs.ReviewControllerDocs;
import com.kiri.server.domain.review.dto.ReviewReqDTO;
import com.kiri.server.domain.review.dto.ReviewResDTO;
import com.kiri.server.domain.review.exception.code.ReviewSuccessCode;
import com.kiri.server.domain.review.servide.ReviewQueryService;
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
    private final ReviewQueryService reviewQueryService;

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

    @Override
    @GetMapping("/{scheduleId}/reviews")
    public ApiResponse<ReviewResDTO.ScheduleReviews> getScheduleReviews(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long scheduleId
    ) {
        return ApiResponse.onSuccess(
                ReviewSuccessCode.REVIEW_LIST,
                reviewQueryService.getScheduleReviews(memberId, scheduleId));
    }

    @Override
    @PutMapping("/{scheduleId}/reviews/{reviewId}")
    public ApiResponse<ReviewResDTO.Create> updateReview(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long scheduleId,
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewReqDTO.Create dto
    ) {
        BaseSuccessCode code = ReviewSuccessCode.REVIEW_UPDATED;
        return ApiResponse.onSuccess(code, reviewService.updateReview(memberId, scheduleId, reviewId, dto));
    }

    @Override
    @DeleteMapping("/{scheduleId}/reviews/{reviewId}")
    public ApiResponse<Void> deleteReview(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long scheduleId,
            @PathVariable Long reviewId
    ) {
        reviewService.deleteReview(memberId, scheduleId, reviewId);
        BaseSuccessCode code = ReviewSuccessCode.REVIEW_DELETED;
        return ApiResponse.onSuccess(code);
    }
}
