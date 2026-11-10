package com.kiri.server.domain.review.servide;

import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.bucket.enums.BucketStatus;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.image.event.ImageDeletedEvent;
import com.kiri.server.domain.image.exception.ImageException;
import com.kiri.server.domain.image.exception.code.ImageErrorCode;
import com.kiri.server.domain.image.service.ImageService;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.entity.FamilyMissionRecommendation;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.repository.FamilyMissionRecommendationRepository;
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
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.domain.schedule.exception.ScheduleException;
import com.kiri.server.domain.schedule.exception.code.ScheduleErrorCode;
import com.kiri.server.domain.schedule.repository.ScheduleParticipantRepository;
import com.kiri.server.domain.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewPhotoRepository reviewPhotoRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ScheduleParticipantRepository scheduleParticipantRepository;

    @Mock
    private ImageService imageService;

    @Mock
    private FamilyMissionRecommendationRepository familyMissionRecommendationRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private ReviewService reviewService;

    private Schedule schedule;
    private ScheduleParticipant scheduleParticipant;

    private final Long memberId = 1L;
    private final Long scheduleId = 10L;
    private final Long scheduleParticipantId = 100L;

    @BeforeEach
    void setUp() {
        schedule = Schedule.builder()
                .id(scheduleId)
                .scheduleDate(LocalDate.now())
                .scheduleTime(LocalTime.now())
                .status(ScheduleStatus.CONFIRMED)
                .build();

        scheduleParticipant = ScheduleParticipant.builder()
                .id(scheduleParticipantId)
                .schedule(schedule)
                .status(ParticipantStatus.ACCEPTED)
                .build();
    }

    private ReviewReqDTO.Create createRequest() {
        return new ReviewReqDTO.Create("가족과 함께 즐거운 시간을 보냈습니다.", 5, true, List.of("review/1/image-1.jpg", "review/1/image-2.jpg"));
    }

    @Test
    @DisplayName("리뷰를 정상적으로 작성한다")
    void createReview() {
        ReviewReqDTO.Create request = createRequest();

        when(scheduleRepository.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(schedule));

        when(scheduleParticipantRepository.findBySchedule_IdAndMember_IdForUpdate(scheduleId, memberId)).thenReturn(Optional.of(scheduleParticipant));

        when(reviewRepository.existsBySchedule_IdAndScheduleParticipant_Id(scheduleId, scheduleParticipantId)).thenReturn(false);

        when(reviewRepository.save(any(Review.class)))
                .thenAnswer(invocation -> {
                    Review review = invocation.getArgument(0);
                    ReflectionTestUtils.setField(review, "id", 1L);
                    return review;
        });

        ReviewResDTO.Create response = reviewService.createReview(memberId, scheduleId, request);

        assertThat(response.reviewId()).isEqualTo(1L);
        assertThat(response.scheduleId()).isEqualTo(scheduleId);
        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.COMPLETED);

        verify(reviewRepository).save(any(Review.class));
        verify(reviewPhotoRepository, times(2)).save(any(ReviewPhoto.class));
    }

    @Test
    @DisplayName("존재하지 않는 일정이면 리뷰를 작성할 수 없다")
    void createReviewWithNonExistentSchedule() {
        ReviewReqDTO.Create request = createRequest();

        when(scheduleRepository.findByIdForUpdate(scheduleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.createReview(memberId, scheduleId, request))
                .isInstanceOf(ScheduleException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_NOT_FOUND);

        verify(reviewRepository, never()).save(any());
        verify(reviewPhotoRepository, never()).save(any());
    }

    @Test
    @DisplayName("일정의 참여자가 아니면 리뷰를 작성할 수 없다")
    void createReviewWithNonParticipant() {
        ReviewReqDTO.Create request = createRequest();

        when(scheduleRepository.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(schedule));

        when(scheduleParticipantRepository.findBySchedule_IdAndMember_IdForUpdate(scheduleId, memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.createReview(memberId, scheduleId, request))
                .isInstanceOf(ReviewException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCode.NOT_PARTICIPANT);

        verify(reviewRepository, never()).save(any());
        verify(reviewPhotoRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 리뷰를 작성했다면 중복으로 작성할 수 없다")
    void createReviewWithDuplicateReview() {
        ReviewReqDTO.Create request = createRequest();

        when(scheduleRepository.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(schedule));

        when(scheduleParticipantRepository.findBySchedule_IdAndMember_IdForUpdate(scheduleId, memberId)).thenReturn(Optional.of(scheduleParticipant));

        when(reviewRepository.existsBySchedule_IdAndScheduleParticipant_Id(scheduleId, scheduleParticipantId)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.createReview(memberId, scheduleId, request))
                .isInstanceOf(ReviewException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCode.DUPLICATE_REVIEW);

        verify(reviewRepository, never()).save(any());
        verify(reviewPhotoRepository, never()).save(any());
    }

    @Test
    @DisplayName("첫 리뷰가 작성되면 일정이 COMPLETED로 변경된다")
    void createReviewCompletesSchedule() {
        ReviewReqDTO.Create request = createRequest();

        when(scheduleRepository.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(schedule));
        when(scheduleParticipantRepository.findBySchedule_IdAndMember_IdForUpdate(scheduleId, memberId)).thenReturn(Optional.of(scheduleParticipant));
        when(reviewRepository.existsBySchedule_IdAndScheduleParticipant_Id(scheduleId, scheduleParticipantId)).thenReturn(false);
        when(reviewRepository.save(any(Review.class)))
                .thenAnswer(invocation -> {
                    Review review = invocation.getArgument(0);
                    return Review.builder()
                            .id(1L)
                            .schedule(schedule)
                            .scheduleParticipant(scheduleParticipant)
                            .content(review.getContent())
                            .starRating(review.getStarRating())
                            .isRejoinIntended(review.getIsRejoinIntended())
                            .build();
                });

        reviewService.createReview(memberId, scheduleId, request);

        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.COMPLETED);
        assertThat(schedule.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("확정된 이미지 key를 리뷰 사진에 저장한다")
    void createReviewSavesConfirmedImageKey() {
        ReviewReqDTO.Create request = createRequest();

        when(scheduleRepository.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(schedule));
        when(scheduleParticipantRepository.findBySchedule_IdAndMember_IdForUpdate(scheduleId, memberId)).thenReturn(Optional.of(scheduleParticipant));
        when(reviewRepository.existsBySchedule_IdAndScheduleParticipant_Id(scheduleId, scheduleParticipantId)).thenReturn(false);
        when(imageService.confirmUpload(memberId, "review/1/image-1.jpg")).thenReturn("user/original/1/image-1.jpg");
        when(imageService.confirmUpload(memberId, "review/1/image-2.jpg")).thenReturn("user/original/1/image-2.jpg");

        reviewService.createReview(memberId, scheduleId, request);

        ArgumentCaptor<ReviewPhoto> captor = ArgumentCaptor.forClass(ReviewPhoto.class);
        verify(reviewPhotoRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ReviewPhoto::getImageKey)
                .containsExactly("user/original/1/image-1.jpg", "user/original/1/image-2.jpg");
    }

    @Test
    @DisplayName("이미지 확정에 실패하면 리뷰를 작성할 수 없다")
    void createReviewWithImageConfirmFailure() {
        ReviewReqDTO.Create request = createRequest();

        when(scheduleRepository.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(schedule));
        when(scheduleParticipantRepository.findBySchedule_IdAndMember_IdForUpdate(scheduleId, memberId)).thenReturn(Optional.of(scheduleParticipant));
        when(reviewRepository.existsBySchedule_IdAndScheduleParticipant_Id(scheduleId, scheduleParticipantId)).thenReturn(false);
        when(imageService.confirmUpload(memberId, "review/1/image-1.jpg"))
                .thenThrow(new ImageException(ImageErrorCode.IMAGE_NOT_UPLOADED));

        assertThatThrownBy(() -> reviewService.createReview(memberId, scheduleId, request))
                .isInstanceOf(ImageException.class)
                .extracting("errorCode")
                .isEqualTo(ImageErrorCode.IMAGE_NOT_UPLOADED);

        verify(reviewPhotoRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 OMPLETED인 일정도 다른 참여자가 리뷰를 작성할 수 있다")
    void createReviewOnCompletedSchedule() {
        schedule.complete(LocalDateTime.now());

        ReviewReqDTO.Create request = createRequest();

        when(scheduleRepository.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(schedule));
        when(scheduleParticipantRepository.findBySchedule_IdAndMember_IdForUpdate(scheduleId, memberId)).thenReturn(Optional.of(scheduleParticipant));
        when(reviewRepository.existsBySchedule_IdAndScheduleParticipant_Id(scheduleId, scheduleParticipantId)).thenReturn(false);
        when(reviewRepository.save(any(Review.class)))
                .thenAnswer(invocation -> {
                    Review review = invocation.getArgument(0);
                    ReflectionTestUtils.setField(review, "id", 2L);
                    return review;
                });

        ReviewResDTO.Create response = reviewService.createReview(memberId, scheduleId, request);

        assertThat(response.reviewId()).isEqualTo(2L);
        assertThat(response.scheduleId()).isEqualTo(scheduleId);
        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.COMPLETED);

        verify(reviewRepository).save(any(Review.class));
        verify(reviewPhotoRepository, times(2)).save(any(ReviewPhoto.class));
    }

    private Schedule scheduleWithBucket(BucketItem bucketItem) {
        return Schedule.builder()
                .id(scheduleId)
                .bucketItem(bucketItem)
                .scheduleDate(LocalDate.now())
                .scheduleTime(LocalTime.now())
                .status(ScheduleStatus.CONFIRMED)
                .build();
    }

    private void stubCreateFlow(Schedule target, ScheduleParticipant participant) {
        when(scheduleRepository.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(target));
        when(scheduleParticipantRepository.findBySchedule_IdAndMember_IdForUpdate(scheduleId, memberId)).thenReturn(Optional.of(participant));
        when(reviewRepository.existsBySchedule_IdAndScheduleParticipant_Id(scheduleId, scheduleParticipantId)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            ReflectionTestUtils.setField(review, "id", 1L);
            return review;
        });
    }

    @Test
    @DisplayName("첫 리뷰로 일정이 완료되면 연결된 버킷도 COMPLETED로 변경된다")
    void createReviewCompletesLinkedBucket() {
        Mission mission = Mission.builder().id(500L).build();
        BucketItem bucketItem = BucketItem.builder()
                .id(700L).family(Family.builder().id(1L).build()).mission(mission)
                .status(BucketStatus.SCHEDULED).build();
        Schedule target = scheduleWithBucket(bucketItem);
        ScheduleParticipant participant = ScheduleParticipant.builder()
                .id(scheduleParticipantId).schedule(target).status(ParticipantStatus.ACCEPTED).build();
        stubCreateFlow(target, participant);

        reviewService.createReview(memberId, scheduleId, createRequest());

        assertThat(bucketItem.getStatus()).isEqualTo(BucketStatus.COMPLETED);
        assertThat(bucketItem.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("주간 미션으로 완료된 버킷이면 이번 주 추천 행의 completed_at을 기록한다")
    void createReviewCompletesWeeklyRecommendation() {
        Mission mission = Mission.builder().id(500L).build();
        BucketItem bucketItem = BucketItem.builder()
                .id(700L).family(Family.builder().id(1L).build()).mission(mission)
                .status(BucketStatus.SCHEDULED).build();
        FamilyMissionRecommendation recommendation = FamilyMissionRecommendation.builder()
                .id(900L).mission(mission).build();
        Schedule target = scheduleWithBucket(bucketItem);
        ScheduleParticipant participant = ScheduleParticipant.builder()
                .id(scheduleParticipantId).schedule(target).status(ParticipantStatus.ACCEPTED).build();
        stubCreateFlow(target, participant);
        when(familyMissionRecommendationRepository
                .findFirstByFamilyIdAndStartedAtLessThanEqualAndEndedAtGreaterThanOrderByStartedAtDesc(anyLong(), any(), any()))
                .thenReturn(Optional.of(recommendation));

        reviewService.createReview(memberId, scheduleId, createRequest());

        assertThat(bucketItem.getStatus()).isEqualTo(BucketStatus.COMPLETED);
        assertThat(recommendation.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("이번 주 추천이 이미 완료 상태면 다시 기록하지 않는다")
    void doesNotRecompleteAlreadyCompletedRecommendation() {
        LocalDateTime firstCompletedAt = LocalDateTime.now().minusHours(1);
        Mission mission = Mission.builder().id(500L).build();
        BucketItem bucketItem = BucketItem.builder()
                .id(700L).family(Family.builder().id(1L).build()).mission(mission)
                .status(BucketStatus.SCHEDULED).build();
        FamilyMissionRecommendation recommendation = FamilyMissionRecommendation.builder()
                .id(900L).mission(mission).completedAt(firstCompletedAt).build();
        Schedule target = scheduleWithBucket(bucketItem);
        ScheduleParticipant participant = ScheduleParticipant.builder()
                .id(scheduleParticipantId).schedule(target).status(ParticipantStatus.ACCEPTED).build();
        stubCreateFlow(target, participant);
        when(familyMissionRecommendationRepository
                .findFirstByFamilyIdAndStartedAtLessThanEqualAndEndedAtGreaterThanOrderByStartedAtDesc(anyLong(), any(), any()))
                .thenReturn(Optional.of(recommendation));

        reviewService.createReview(memberId, scheduleId, createRequest());

        assertThat(recommendation.getCompletedAt()).isEqualTo(firstCompletedAt);
    }

    @Test
    @DisplayName("완료된 버킷의 미션이 이번 주 추천 미션과 다르면 추천을 완료하지 않는다")
    void doesNotCompleteRecommendationWhenMissionDiffers() {
        BucketItem bucketItem = BucketItem.builder()
                .id(700L).family(Family.builder().id(1L).build())
                .mission(Mission.builder().id(500L).build())
                .status(BucketStatus.SCHEDULED).build();
        FamilyMissionRecommendation recommendation = FamilyMissionRecommendation.builder()
                .id(900L).mission(Mission.builder().id(999L).build()).build();
        Schedule target = scheduleWithBucket(bucketItem);
        ScheduleParticipant participant = ScheduleParticipant.builder()
                .id(scheduleParticipantId).schedule(target).status(ParticipantStatus.ACCEPTED).build();
        stubCreateFlow(target, participant);
        when(familyMissionRecommendationRepository
                .findFirstByFamilyIdAndStartedAtLessThanEqualAndEndedAtGreaterThanOrderByStartedAtDesc(anyLong(), any(), any()))
                .thenReturn(Optional.of(recommendation));

        reviewService.createReview(memberId, scheduleId, createRequest());

        assertThat(bucketItem.getStatus()).isEqualTo(BucketStatus.COMPLETED);
        assertThat(recommendation.getCompletedAt()).isNull();
    }

    private final Long reviewId = 1L;
    private final Family family = Family.builder().id(1L).build();

    private Review reviewWrittenBy(Long writerId) {
        Member writer = Member.builder().id(writerId).family(family).build();
        Schedule familySchedule = Schedule.builder().id(scheduleId).family(family).build();
        ScheduleParticipant participant = ScheduleParticipant.builder()
                .id(scheduleParticipantId).schedule(familySchedule).member(writer).build();
        return Review.builder()
                .id(reviewId).schedule(familySchedule).scheduleParticipant(participant)
                .content("기존 내용").starRating(3).isRejoinIntended(false)
                .build();
    }

    private ReviewPhoto photo(Long id, Review review, String key, int order) {
        return ReviewPhoto.builder().id(id).review(review).imageKey(key).displayOrder(order).build();
    }

    @Test
    @DisplayName("리뷰 수정 시 유지한 사진은 순서만 바꾸고, 새 키는 확정하고, 빠진 사진은 삭제한다")
    void updateReview() {
        Review review = reviewWrittenBy(memberId);
        ReviewPhoto keptA = photo(1L, review, "user/original/1/a.jpg", 1);
        ReviewPhoto removedB = photo(2L, review, "user/original/1/b.jpg", 2);
        ReviewPhoto keptC = photo(3L, review, "user/original/1/c.jpg", 3);
        ReviewReqDTO.Create request = new ReviewReqDTO.Create("수정한 내용", 5, true,
                List.of("user/original/1/c.jpg", "pending/user/1/d.jpg", "user/original/1/a.jpg"));

        when(reviewRepository.findByIdAndScheduleIdWithWriter(reviewId, scheduleId)).thenReturn(Optional.of(review));
        when(reviewPhotoRepository.findByReview_Id(reviewId)).thenReturn(List.of(keptA, removedB, keptC));
        when(imageService.confirmUpload(memberId, "pending/user/1/d.jpg")).thenReturn("user/original/1/d.jpg");

        reviewService.updateReview(memberId, scheduleId, reviewId, request);

        assertThat(review.getContent()).isEqualTo("수정한 내용");
        assertThat(review.getStarRating()).isEqualTo(5);
        assertThat(review.getIsRejoinIntended()).isTrue();

        assertThat(keptC.getDisplayOrder()).isEqualTo(1);
        assertThat(keptA.getDisplayOrder()).isEqualTo(3);

        ArgumentCaptor<ReviewPhoto> saved = ArgumentCaptor.forClass(ReviewPhoto.class);
        verify(reviewPhotoRepository).save(saved.capture());
        assertThat(saved.getValue().getImageKey()).isEqualTo("user/original/1/d.jpg");
        assertThat(saved.getValue().getDisplayOrder()).isEqualTo(2);

        verify(reviewPhotoRepository).deleteAll(List.of(removedB));
        verify(eventPublisher).publishEvent(new ImageDeletedEvent(List.of("user/original/1/b.jpg")));
    }

    @Test
    @DisplayName("존재하지 않거나 해당 일정의 리뷰가 아니면 수정할 수 없다")
    void updateReviewNotFound() {
        when(reviewRepository.findByIdAndScheduleIdWithWriter(reviewId, scheduleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.updateReview(memberId, scheduleId, reviewId, createRequest()))
                .isInstanceOf(ReviewException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND);
    }

    @Test
    @DisplayName("같은 가족이라도 본인이 작성한 리뷰가 아니면 수정할 수 없다")
    void updateReviewByFamilyNonWriter() {
        Long familyMemberId = 2L;
        when(reviewRepository.findByIdAndScheduleIdWithWriter(reviewId, scheduleId)).thenReturn(Optional.of(reviewWrittenBy(memberId)));
        when(memberRepository.findById(familyMemberId))
                .thenReturn(Optional.of(Member.builder().id(familyMemberId).family(family).build()));

        assertThatThrownBy(() -> reviewService.updateReview(familyMemberId, scheduleId, reviewId, createRequest()))
                .isInstanceOf(ReviewException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCode.NOT_REVIEW_WRITER);
    }

    @Test
    @DisplayName("다른 가족의 리뷰는 수정할 수 없다")
    void updateReviewByOtherFamily() {
        Long outsiderId = 3L;
        when(reviewRepository.findByIdAndScheduleIdWithWriter(reviewId, scheduleId)).thenReturn(Optional.of(reviewWrittenBy(memberId)));
        when(memberRepository.findById(outsiderId))
                .thenReturn(Optional.of(Member.builder().id(outsiderId).family(Family.builder().id(2L).build()).build()));

        assertThatThrownBy(() -> reviewService.updateReview(outsiderId, scheduleId, reviewId, createRequest()))
                .isInstanceOf(ReviewException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCode.FORBIDDEN_FAMILY);
    }

    @Test
    @DisplayName("리뷰를 삭제하면 사진과 리뷰를 지우고 커밋 후 S3 삭제 이벤트를 발행한다")
    void deleteReview() {
        Review review = reviewWrittenBy(memberId);
        List<ReviewPhoto> photos = List.of(
                photo(1L, review, "user/original/1/a.jpg", 1),
                photo(2L, review, "user/original/1/b.jpg", 2));

        when(reviewRepository.findByIdAndScheduleIdWithWriter(reviewId, scheduleId)).thenReturn(Optional.of(review));
        when(reviewPhotoRepository.findByReview_Id(reviewId)).thenReturn(photos);

        reviewService.deleteReview(memberId, scheduleId, reviewId);

        verify(reviewPhotoRepository).deleteAll(photos);
        verify(reviewRepository).delete(review);
        verify(eventPublisher).publishEvent(new ImageDeletedEvent(List.of("user/original/1/a.jpg", "user/original/1/b.jpg")));
    }
}