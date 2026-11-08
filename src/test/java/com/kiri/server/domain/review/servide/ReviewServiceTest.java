package com.kiri.server.domain.review.servide;

import com.kiri.server.domain.image.exception.ImageException;
import com.kiri.server.domain.image.exception.code.ImageErrorCode;
import com.kiri.server.domain.image.service.ImageService;
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
}