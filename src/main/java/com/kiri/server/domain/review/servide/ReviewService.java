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
import java.util.Set;
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

    // 요청 목록에 있는 기존 키 그대로 사용 / 새 키 업로드 확정 / 빠진 기존 키는 커밋 후 S3에서 지움
    private void replacePhotos(Long memberId, Review review, List<String> imageKeys) {
        List<ReviewPhoto> existingPhotos = reviewPhotoRepository.findByReview_Id(review.getId());
        Set<String> existingKeys = existingPhotos.stream()
                .map(ReviewPhoto::getImageKey)
                .collect(Collectors.toSet());

        reviewPhotoRepository.deleteAll(existingPhotos);

        Set<String> keptKeys = new HashSet<>();
        for (int i = 0; i < imageKeys.size(); i++) {
            String key = imageKeys.get(i);
            String confirmedKey = existingKeys.contains(key) ? key : imageService.confirmUpload(memberId, key);
            keptKeys.add(confirmedKey);
            reviewPhotoRepository.save(ReviewPhotoConverter.toReviewPhoto(review, confirmedKey, i + 1));
        }

        List<String> removedKeys = existingKeys.stream()
                .filter(key -> !keptKeys.contains(key))
                .toList();
        if (!removedKeys.isEmpty()) {
            eventPublisher.publishEvent(new ImageDeletedEvent(removedKeys));
        }
    }
}
