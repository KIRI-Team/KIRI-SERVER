package com.kiri.server.domain.review.servide;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.image.service.ImageUrlService;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.review.converter.ReviewConverter;
import com.kiri.server.domain.review.dto.ReviewResDTO;
import com.kiri.server.domain.review.entity.Review;
import com.kiri.server.domain.review.exception.ReviewException;
import com.kiri.server.domain.review.exception.code.ReviewErrorCode;
import com.kiri.server.domain.review.repository.ReviewPhotoRepository;
import com.kiri.server.domain.review.repository.ReviewRepository;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.exception.ScheduleException;
import com.kiri.server.domain.schedule.exception.code.ScheduleErrorCode;
import com.kiri.server.domain.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewQueryService {

    private final MemberRepository memberRepository;
    private final ScheduleRepository scheduleRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewPhotoRepository reviewPhotoRepository;
    private final ImageUrlService imageUrlService;

    @Transactional(readOnly = true)
    public ReviewResDTO.ScheduleReviews getScheduleReviews(Long memberId, Long scheduleId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));
        Family family = member.getFamily();
        if (family == null) {
            throw new ReviewException(ReviewErrorCode.FORBIDDEN_FAMILY);
        }

        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        if (schedule.getFamily() == null || !schedule.getFamily().getId().equals(family.getId())) {
            throw new ReviewException(ReviewErrorCode.FORBIDDEN_FAMILY);
        }

        List<Review> reviews = reviewRepository.findAllByScheduleIdWithWriter(scheduleId);
        if (reviews.isEmpty()) {
            return ReviewConverter.toScheduleReviews(scheduleId, List.of());
        }

        List<Long> reviewIds = reviews.stream().map(Review::getId).toList();
        Map<Long, List<String>> imageUrlsByReview = reviewPhotoRepository
                .findByReview_IdInOrderByReview_IdAscDisplayOrderAsc(reviewIds).stream()
                .collect(Collectors.groupingBy(
                        photo -> photo.getReview().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(photo -> imageUrlService.toUrl(photo.getImageKey()), Collectors.toList())
                ));

        List<ReviewResDTO.Item> items = reviews.stream()
                .map(review -> ReviewConverter.toItem(
                        review,
                        imageUrlsByReview.getOrDefault(review.getId(), List.of()),
                        isMine(review, memberId)))
                .toList();

        return ReviewConverter.toScheduleReviews(scheduleId, items);
    }

    private boolean isMine(Review review, Long memberId) {
        Member writer = review.getScheduleParticipant().getMember();
        return writer != null && writer.getId().equals(memberId);
    }
}
