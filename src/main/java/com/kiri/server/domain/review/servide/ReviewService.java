package com.kiri.server.domain.review.servide;

import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.bucket.enums.BucketStatus;
import com.kiri.server.domain.image.service.ImageService;
import com.kiri.server.domain.mission.repository.FamilyMissionRecommendationRepository;
import com.kiri.server.domain.review.converter.ReviewConverter;
import com.kiri.server.domain.review.converter.ReviewPhotoConverter;
import com.kiri.server.domain.review.dto.ReviewReqDTO;
import com.kiri.server.domain.review.dto.ReviewResDTO;
import com.kiri.server.domain.review.entity.Review;
import com.kiri.server.domain.review.entity.ReviewPhoto;
import com.kiri.server.domain.review.exception.ReviewException;
import com.kiri.server.domain.review.exception.code.ReviewErrorCode;
import com.kiri.server.domain.review.repository.ReviewPhotoRepository;
import com.kiri.server.domain.review.repository.ReviewRepository;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.domain.schedule.exception.ScheduleException;
import com.kiri.server.domain.schedule.exception.code.ScheduleErrorCode;
import com.kiri.server.domain.schedule.repository.ScheduleParticipantRepository;
import com.kiri.server.domain.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewPhotoRepository reviewPhotoRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleParticipantRepository scheduleParticipantRepository;
    private final ImageService imageService;
    private final FamilyMissionRecommendationRepository familyMissionRecommendationRepository;

    @Transactional
    public ReviewResDTO.Create createReview(Long memberId, Long scheduleId, ReviewReqDTO.Create dto) {
        Schedule schedule = scheduleRepository.findByIdForUpdate(scheduleId).orElseThrow(() -> new ScheduleException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));

        if (schedule.getStatus() == ScheduleStatus.CANCELLED || schedule.getStatus() == ScheduleStatus.NOT_CONFIRMED) {
            throw new ReviewException(ReviewErrorCode.INVALID_SCHEDULE_STATE);
        }

        ScheduleParticipant scheduleParticipant = scheduleParticipantRepository.findBySchedule_IdAndMember_IdForUpdate(scheduleId, memberId).orElseThrow(() -> new ReviewException(ReviewErrorCode.NOT_PARTICIPANT));

        if (reviewRepository.existsBySchedule_IdAndScheduleParticipant_Id(
                scheduleId,
                scheduleParticipant.getId()
        )) {
            throw new ReviewException(ReviewErrorCode.DUPLICATE_REVIEW);
        }

        Review review = ReviewConverter.toReview(schedule, scheduleParticipant, dto);

        reviewRepository.save(review);

        for (int i = 0; i < dto.imageKeys().size(); i++) {
            String confirmedKey=imageService.confirmUpload(memberId, dto.imageKeys().get(i));
            ReviewPhoto reviewPhoto = ReviewPhotoConverter.toReviewPhoto(review, confirmedKey, i + 1);
            reviewPhotoRepository.save(reviewPhoto);
        }

        if (schedule.getStatus() != ScheduleStatus.COMPLETED) {
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
            schedule.complete(now);

            BucketItem bucketItem = schedule.getBucketItem();
            if (bucketItem != null && bucketItem.getStatus() != BucketStatus.COMPLETED) {
                bucketItem.markCompleted(now);

                familyMissionRecommendationRepository
                        .findFirstByFamilyIdAndStartedAtLessThanEqualAndEndedAtGreaterThanOrderByStartedAtDesc(
                                bucketItem.getFamily().getId(), now, now)
                        .filter(recommendation -> recommendation.getCompletedAt() == null
                                && recommendation.getMission().getId().equals(bucketItem.getMission().getId()))
                        .ifPresent(recommendation -> recommendation.markCompleted(now));
            }
        }

        return ReviewConverter.toCreateResponse(review);
    }
}
