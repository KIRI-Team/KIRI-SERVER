package com.kiri.server.domain.taste.service;

import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.bucket.repository.BucketItemRepository;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.question.entity.MemberAnswer;
import com.kiri.server.domain.question.entity.MemberAnswerCategory;
import com.kiri.server.domain.question.repository.MemberAnswerCategoryRepository;
import com.kiri.server.domain.question.repository.MemberAnswerRepository;
import com.kiri.server.domain.review.entity.Review;
import com.kiri.server.domain.review.repository.ReviewRepository;
import com.kiri.server.domain.taste.caculator.FamilyTasteScoreCalculator;
import com.kiri.server.domain.taste.caculator.MemberTasteScoreCalculator;
import com.kiri.server.domain.taste.dto.TasteSourceDTO;
import com.kiri.server.domain.taste.entity.MemberTaste;
import com.kiri.server.domain.taste.repository.MemberTasteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 여러 회원의 취향을 합친 미션별 취향 점수 (가족 미션 추천·일정 근처 장소 추천 공용)
// - 명시적 취향(취향 테스트·취향 질문)을 우선하고, 활동 이력(버킷 저장·후기)을 보조 신호로 쓴다 → MemberTasteScoreCalculator
// - 회원별 점수의 평균을 낸다 → FamilyTasteScoreCalculator
@Service
@RequiredArgsConstructor
public class FamilyTasteScoreService {

    private final MemberTasteRepository memberTasteRepository;
    private final MemberAnswerRepository memberAnswerRepository;
    private final MemberAnswerCategoryRepository memberAnswerCategoryRepository;
    private final BucketItemRepository bucketItemRepository;
    private final ReviewRepository reviewRepository;
    private final MemberTasteScoreCalculator memberTasteScoreCalculator;
    private final FamilyTasteScoreCalculator familyTasteScoreCalculator;

    // 미션 ID → 취향 점수. 대상 회원이나 미션이 없으면 빈 Map (호출하는 쪽에서 0점으로 처리)
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> calculate(Collection<Long> memberIds, List<Mission> missions, LocalDate today) {
        if (memberIds.isEmpty() || missions.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = List.copyOf(memberIds);

        // 회원들의 취향 및 행동 데이터 일괄 조회
        Map<Long, MemberTaste> memberTasteMap = memberTasteRepository.findAllWithCategoriesByMemberIdIn(ids).stream()
                .collect(Collectors.toMap(taste -> taste.getMember().getId(), Function.identity()));
        List<MemberAnswer> answers = memberAnswerRepository.findAllByMember_IdIn(ids);
        List<Long> answerIds = answers.stream().map(MemberAnswer::getId).toList();
        List<MemberAnswerCategory> answerCategories = answerIds.isEmpty()
                ? List.of()
                : memberAnswerCategoryRepository.findAllByMemberAnswerIdIn(answerIds);
        List<BucketItem> bucketItems = bucketItemRepository.findAllByMember_IdIn(ids);
        List<Review> reviews = reviewRepository.findAllCompletedReviewsByMemberIds(ids);

        // 회원별 데이터 그룹화
        Map<Long, List<MemberAnswer>> answersByMember = answers.stream()
                .collect(Collectors.groupingBy(answer -> answer.getMember().getId()));
        Map<Long, List<MemberAnswerCategory>> answerCategoriesByMember = answerCategories.stream()
                .collect(Collectors.groupingBy(category -> category.getMemberAnswer().getMember().getId()));
        Map<Long, List<BucketItem>> bucketItemsByMember = bucketItems.stream()
                .filter(item -> item.getMember() != null)
                .collect(Collectors.groupingBy(item -> item.getMember().getId()));
        Map<Long, List<Review>> reviewsByMember = reviews.stream()
                .collect(Collectors.groupingBy(review -> review.getScheduleParticipant().getMember().getId()));

        Map<Long, BigDecimal> scores = new HashMap<>();
        for (Mission mission : missions) {
            List<TasteSourceDTO.MemberScore> memberScores = new ArrayList<>();
            for (Long memberId : ids) {
                MemberTaste memberTaste = memberTasteMap.get(memberId);
                List<MemberAnswer> memberAnswers = answersByMember.getOrDefault(memberId, List.of());
                List<BucketItem> memberBucketItems = bucketItemsByMember.getOrDefault(memberId, List.of());
                List<Review> memberReviews = reviewsByMember.getOrDefault(memberId, List.of());

                // 해당 회원에게 취향 및 행동 데이터가 있는 경우에만 평균에 포함
                boolean hasTasteSignal = memberTaste != null || !memberAnswers.isEmpty()
                        || !memberBucketItems.isEmpty() || !memberReviews.isEmpty();
                if (!hasTasteSignal) {
                    continue;
                }
                memberScores.add(memberTasteScoreCalculator.calculate(memberTaste, memberAnswers,
                        answerCategoriesByMember.getOrDefault(memberId, List.of()), memberBucketItems, memberReviews,
                        mission, today));
            }
            scores.put(mission.getId(), familyTasteScoreCalculator.calculate(memberScores));
        }
        return scores;
    }
}
