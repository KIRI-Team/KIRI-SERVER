package com.kiri.server.domain.review.converter;

import com.kiri.server.domain.review.dto.ReviewReqDTO;
import com.kiri.server.domain.review.dto.ReviewResDTO;
import com.kiri.server.domain.review.entity.Review;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;

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
}
