package com.kiri.server.domain.review.converter;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.review.dto.ReviewReqDTO;
import com.kiri.server.domain.review.dto.ReviewResDTO;
import com.kiri.server.domain.review.entity.Review;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;

import java.util.List;

public class ReviewConverter {

    public static Review toReview(Schedule schedule, ScheduleParticipant scheduleParticipant, ReviewReqDTO.Create dto) {
        return Review.builder()
                .schedule(schedule)
                .scheduleParticipant(scheduleParticipant)
                .content(dto.content())
                .starRating(dto.starRating())
                .isRejoinIntended(dto.isRejoinIntended())
                .build();
    }

    public static ReviewResDTO.Create toCreateResponse(Review review) {
        return ReviewResDTO.Create.builder()
                .reviewId(review.getId())
                .scheduleId(review.getSchedule().getId())
                .build();
    }

    public static ReviewResDTO.Writer toWriter(Member member) {
        if (member == null) {
            return ReviewResDTO.Writer.builder()
                    .nickname("탈퇴한 사용자")
                    .build();
        }
        return ReviewResDTO.Writer.builder()
                .memberId(member.getId())
                .nickname(member.getDisplayName())
                .familyRole(member.getFamilyRole())
                .build();
    }

    public static ReviewResDTO.Item toItem(Review review, List<String> imageUrls, boolean isMine) {
        return ReviewResDTO.Item.builder()
                .reviewId(review.getId())
                .writer(toWriter(review.getScheduleParticipant().getMember()))
                .content(review.getContent())
                .starRating(review.getStarRating())
                .isRejoinIntended(review.getIsRejoinIntended())
                .imageUrls(imageUrls)
                .createdAt(review.getCreatedAt())
                .isMine(isMine)
                .build();
    }

    public static ReviewResDTO.ScheduleReviews toScheduleReviews(Long scheduleId, List<ReviewResDTO.Item> items) {
        return ReviewResDTO.ScheduleReviews.builder()
                .scheduleId(scheduleId)
                .reviews(items)
                .build();
    }
}
