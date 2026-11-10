package com.kiri.server.domain.review.servide;

import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.bucket.enums.BucketStatus;
import com.kiri.server.domain.image.event.ImageDeletedEvent;
import com.kiri.server.domain.image.service.ImageService;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewPhotoRepository reviewPhotoRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleParticipantRepository scheduleParticipantRepository;
    private final ImageService imageService;
    private final FamilyMissionRecommendationRepository familyMissionRecommendationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MemberRepository memberRepository;

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

    @Transactional
    public ReviewResDTO.Create updateReview(Long memberId, Long scheduleId, Long reviewId, ReviewReqDTO.Create dto) {
        Review review = getOwnReview(memberId, scheduleId, reviewId);

        review.update(dto.content(), dto.starRating(), dto.isRejoinIntended());
        replacePhotos(memberId, review, dto.imageKeys());

        return ReviewConverter.toCreateResponse(review);
    }

    @Transactional
    public void deleteReview(Long memberId, Long scheduleId, Long reviewId) {
        Review review = getOwnReview(memberId, scheduleId, reviewId);

        List<ReviewPhoto> photos = reviewPhotoRepository.findByReview_Id(review.getId());
        List<String> imageKeys = photos.stream()
                .map(ReviewPhoto::getImageKey)
                .toList();

        reviewPhotoRepository.deleteAll(photos);
        reviewRepository.delete(review);

        if (!imageKeys.isEmpty()) {
            eventPublisher.publishEvent(new ImageDeletedEvent(imageKeys));
        }
    }

    private Review getOwnReview(Long memberId, Long scheduleId, Long reviewId) {
        Review review = reviewRepository.findByIdAndScheduleIdWithWriter(reviewId, scheduleId)
                .orElseThrow(() -> new ReviewException(ReviewErrorCode.REVIEW_NOT_FOUND));

        Member writer = review.getScheduleParticipant().getMember();
        if (writer != null && writer.getId().equals(memberId)) {
            return review;
        }

        // 작성자가 아닐 때 요청자 가족을 조회해 다른 가족 / 같은 가족 비작성자 구분
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));
        Family family = member.getFamily();
        Family scheduleFamily = review.getSchedule().getFamily();
        if (family == null || scheduleFamily == null || !scheduleFamily.getId().equals(family.getId())) {
            throw new ReviewException(ReviewErrorCode.FORBIDDEN_FAMILY);
        }
        throw new ReviewException(ReviewErrorCode.NOT_REVIEW_WRITER);
    }

    // 기존 사진은 행을 유지한 채 순서만 갱신 / 새 키만 업로드 확정 / 빠진 사진은 행 삭제 후 커밋 후 S3에서 지움
    private void replacePhotos(Long memberId, Review review, List<String> imageKeys) {
        Map<String, ReviewPhoto> existingByKey = reviewPhotoRepository.findByReview_Id(review.getId()).stream()
                .collect(Collectors.toMap(ReviewPhoto::getImageKey, Function.identity()));
        Set<String> requestedKeys = new HashSet<>(imageKeys);

        List<ReviewPhoto> removedPhotos = existingByKey.values().stream()
                .filter(photo -> !requestedKeys.contains(photo.getImageKey()))
                .toList();
        reviewPhotoRepository.deleteAll(removedPhotos);

        for (int i = 0; i < imageKeys.size(); i++) {
            String key = imageKeys.get(i);
            ReviewPhoto existing = existingByKey.get(key);
            if (existing != null) {
                existing.changeDisplayOrder(i + 1);
            } else {
                String confirmedKey = imageService.confirmUpload(memberId, key);
                reviewPhotoRepository.save(ReviewPhotoConverter.toReviewPhoto(review, confirmedKey, i + 1));
            }
        }

        if (!removedPhotos.isEmpty()) {
            List<String> removedKeys = removedPhotos.stream().map(ReviewPhoto::getImageKey).toList();
            eventPublisher.publishEvent(new ImageDeletedEvent(removedKeys));
        }
    }
}
