package com.kiri.server.domain.review.servide;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.image.service.ImageUrlService;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.enums.FamilyRole;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
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
import com.kiri.server.domain.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewQueryServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewPhotoRepository reviewPhotoRepository;

    @Mock
    private ImageUrlService imageUrlService;

    @InjectMocks
    private ReviewQueryService reviewQueryService;

    private final Long memberId = 1L;
    private final Long scheduleId = 10L;

    private Family family;
    private Member requester;

    @BeforeEach
    void setUp() {
        family = org.mockito.Mockito.mock(Family.class);
        lenient().when(family.getId()).thenReturn(1L);

        requester = Member.builder()
                .id(memberId)
                .family(family)
                .nickname("김주연")
                .familyRole(FamilyRole.DAUGHTER)
                .build();
    }

    private Review review(Long reviewId, Schedule schedule, Member writer, String content) {
        ScheduleParticipant participant = ScheduleParticipant.builder()
                .id(reviewId * 10)
                .schedule(schedule)
                .member(writer)
                .build();
        return Review.builder()
                .id(reviewId)
                .schedule(schedule)
                .scheduleParticipant(participant)
                .content(content)
                .starRating(5)
                .isRejoinIntended(true)
                .build();
    }

    @Test
    @DisplayName("일정의 리뷰 목록을 작성자·사진과 함께 조회하고 본인 리뷰를 구분한다")
    void getScheduleReviews() {
        Schedule schedule = Schedule.builder()
                .id(scheduleId)
                .family(family)
                .status(ScheduleStatus.COMPLETED)
                .build();

        Member other = Member.builder()
                .id(2L)
                .family(family)
                .nickname("김아빠")
                .familyRole(FamilyRole.FATHER)
                .build();

        Review mine = review(1L, schedule, requester, "처음 가봤다");
        Review others = review(2L, schedule, other, "즐거웠다");

        ReviewPhoto p1 = ReviewPhoto.builder().id(1L).review(mine).imageKey("review/1/a.jpg").displayOrder(1).build();
        ReviewPhoto p2 = ReviewPhoto.builder().id(2L).review(mine).imageKey("review/1/b.jpg").displayOrder(2).build();
        ReviewPhoto p3 = ReviewPhoto.builder().id(3L).review(others).imageKey("review/2/a.jpg").displayOrder(1).build();

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(requester));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
        when(reviewRepository.findAllByScheduleIdWithWriter(scheduleId)).thenReturn(List.of(mine, others));
        when(reviewPhotoRepository.findByReview_IdInOrderByReview_IdAscDisplayOrderAsc(anyList()))
                .thenReturn(List.of(p1, p2, p3));
        when(imageUrlService.toUrl(anyString())).thenAnswer(inv -> "url:" + inv.getArgument(0));

        ReviewResDTO.ScheduleReviews result = reviewQueryService.getScheduleReviews(memberId, scheduleId);

        assertThat(result.scheduleId()).isEqualTo(scheduleId);
        assertThat(result.reviews()).hasSize(2);

        ReviewResDTO.Item first = result.reviews().get(0);
        assertThat(first.reviewId()).isEqualTo(1L);
        assertThat(first.writer().nickname()).isEqualTo("김주연");
        assertThat(first.isMine()).isTrue();
        assertThat(first.photos())
                .extracting(ReviewResDTO.Photo::reviewPhotoId, ReviewResDTO.Photo::imageKey, ReviewResDTO.Photo::imageUrl)
                .containsExactly(
                        tuple(1L, "review/1/a.jpg", "url:review/1/a.jpg"),
                        tuple(2L, "review/1/b.jpg", "url:review/1/b.jpg"));

        ReviewResDTO.Item second = result.reviews().get(1);
        assertThat(second.isMine()).isFalse();
        assertThat(second.photos()).extracting(ReviewResDTO.Photo::imageUrl).containsExactly("url:review/2/a.jpg");
    }

    @Test
    @DisplayName("리뷰가 없으면 빈 목록을 반환한다")
    void getScheduleReviewsEmpty() {
        Schedule schedule = Schedule.builder()
                .id(scheduleId)
                .family(family)
                .status(ScheduleStatus.COMPLETED)
                .build();

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(requester));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
        when(reviewRepository.findAllByScheduleIdWithWriter(scheduleId)).thenReturn(List.of());

        ReviewResDTO.ScheduleReviews result = reviewQueryService.getScheduleReviews(memberId, scheduleId);

        assertThat(result.reviews()).isEmpty();
    }

    @Test
    @DisplayName("요청자 가족의 일정이 아니면 조회할 수 없다")
    void getScheduleReviewsForbiddenOtherFamily() {
        Family otherFamily = org.mockito.Mockito.mock(Family.class);
        when(otherFamily.getId()).thenReturn(2L);

        Schedule schedule = Schedule.builder()
                .id(scheduleId)
                .family(otherFamily)
                .status(ScheduleStatus.COMPLETED)
                .build();

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(requester));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

        assertThatThrownBy(() -> reviewQueryService.getScheduleReviews(memberId, scheduleId))
                .isInstanceOf(ReviewException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCode.FORBIDDEN_FAMILY);
    }

    @Test
    @DisplayName("존재하지 않는 일정이면 조회할 수 없다")
    void getScheduleReviewsScheduleNotFound() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(requester));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewQueryService.getScheduleReviews(memberId, scheduleId))
                .isInstanceOf(ScheduleException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_NOT_FOUND);
    }

    @Test
    @DisplayName("가족 공간이 없는 사용자는 조회할 수 없다")
    void getScheduleReviewsNoFamily() {
        Member noFamily = Member.builder().id(memberId).nickname("무가족").build();
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(noFamily));

        assertThatThrownBy(() -> reviewQueryService.getScheduleReviews(memberId, scheduleId))
                .isInstanceOf(ReviewException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCode.FORBIDDEN_FAMILY);
    }
}
