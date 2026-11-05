package com.kiri.server.domain.mission.service;

import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.bucket.repository.BucketItemRepository;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.family.exception.FamilyException;
import com.kiri.server.domain.family.exception.code.FamilyErrorCode;
import com.kiri.server.domain.family.repository.FamilyRepository;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.converter.MissionConverter;
import com.kiri.server.domain.mission.dto.MissionResDTO;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.repository.MissionRepository;
import com.kiri.server.domain.question.entity.MemberAnswer;
import com.kiri.server.domain.question.entity.MemberAnswerCategory;
import com.kiri.server.domain.question.repository.MemberAnswerCategoryRepository;
import com.kiri.server.domain.question.repository.MemberAnswerRepository;
import com.kiri.server.domain.review.entity.Review;
import com.kiri.server.domain.review.repository.ReviewRepository;
import com.kiri.server.domain.taste.caculator.FamilyTasteScoreCalculator;
import com.kiri.server.domain.taste.caculator.GeneralScoreCalculator;
import com.kiri.server.domain.taste.caculator.MemberTasteScoreCalculator;
import com.kiri.server.domain.taste.caculator.MissionScoreCalculator;
import com.kiri.server.domain.taste.dto.TasteSourceDTO;
import com.kiri.server.domain.taste.entity.MemberTaste;
import com.kiri.server.domain.taste.repository.MemberTasteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MissionRecommendationService {

    private final MemberRepository memberRepository;
    private final MissionRepository missionRepository;

    private final MemberTasteRepository memberTasteRepository;
    private final MemberAnswerRepository memberAnswerRepository;
    private final MemberAnswerCategoryRepository memberAnswerCategoryRepository;
    private final BucketItemRepository bucketItemRepository;
    private final ReviewRepository reviewRepository;
    private final FamilyRepository familyRepository;

    private final MemberTasteScoreCalculator memberTasteScoreCalculator;
    private final FamilyTasteScoreCalculator familyTasteScoreCalculator;
    private final GeneralScoreCalculator generalScoreCalculator;
    private final MissionScoreCalculator missionScoreCalculator;

    @Transactional(readOnly = true)
    public List<MissionResDTO.Recommendation> recommend(Long memberId) {

        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));

        Family family = member.getFamily();

        if (family == null) {
            throw new FamilyException(FamilyErrorCode.NOT_FOUND);
        }

        List<Member> familyMembers = memberRepository.findAllByFamilyAndDeletedAtIsNull(family);

        return calculateRecommendations(family, familyMembers);
    }

    @Transactional(readOnly = true)
    public List<MissionResDTO.Recommendation> recommendByFamilyId(Long familyId) {

        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.NOT_FOUND));

        List<Member> familyMembers = memberRepository.findAllByFamilyAndDeletedAtIsNull(family);

        return calculateRecommendations(family, familyMembers);
    }

    private List<MissionResDTO.Recommendation> calculateRecommendations(Family family, List<Member> familyMembers) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        if (familyMembers == null || familyMembers.isEmpty()) {
            return List.of();
        }

        List<Long> memberIds = familyMembers.stream().map(Member::getId).toList();

        // 3. 가족 구성원의 취향 및 행동 데이터 일괄 조회
        Map<Long, MemberTaste> memberTasteMap =
                memberTasteRepository.findAllWithCategoriesByMemberIdIn(memberIds)
                        .stream()
                        .collect(Collectors.toMap(taste -> taste.getMember().getId(), Function.identity()));

        List<MemberAnswer> answers = memberAnswerRepository.findAllByMember_IdIn(memberIds);

        List<Long> answerIds = answers.stream().map(MemberAnswer::getId).toList();

        List<MemberAnswerCategory> answerCategories = answerIds.isEmpty() ? List.of() : memberAnswerCategoryRepository.findAllByMemberAnswerIdIn(answerIds);

        List<BucketItem> bucketItems = bucketItemRepository.findAllByMember_IdIn(memberIds);

        List<Review> reviews = reviewRepository.findAllCompletedReviewsByMemberIds(memberIds);

        // 4. 회원별 데이터 그룹화
        Map<Long, List<MemberAnswer>> answersByMember = answers.stream().collect(Collectors.groupingBy(answer -> answer.getMember().getId()));

        Map<Long, List<MemberAnswerCategory>> answerCategoriesByMember = answerCategories.stream().collect(Collectors.groupingBy(category -> category.getMemberAnswer().getMember().getId()));

        Map<Long, List<BucketItem>> bucketItemsByMember = bucketItems.stream().filter(item -> item.getMember() != null).collect(Collectors.groupingBy(item -> item.getMember().getId()));

        Map<Long, List<Review>> reviewsByMember = reviews.stream().collect(Collectors.groupingBy(review -> review.getScheduleParticipant().getMember().getId()));

        // 5. 추천 가능한 미션 조회
        List<Mission> missions = missionRepository.findAllRecommendableMissions(today);

        if (missions.isEmpty()) {
            return List.of();
        }

        // 6. 미션별 가족 취향 점수 및 최종 점수 계산
        List<MissionResDTO.Recommendation> recommendations = new ArrayList<>();

        for (Mission mission : missions) {

            List<TasteSourceDTO.MemberScore> memberScores = new ArrayList<>();

            for (Long familyMemberId : memberIds) {

                MemberTaste memberTaste = memberTasteMap.get(familyMemberId);

                List<MemberAnswer> memberAnswers = answersByMember.getOrDefault(familyMemberId, List.of());

                List<MemberAnswerCategory> memberAnswerCategories = answerCategoriesByMember.getOrDefault(familyMemberId, List.of());

                List<BucketItem> memberBucketItems = bucketItemsByMember.getOrDefault(familyMemberId, List.of());

                List<Review> memberReviews = reviewsByMember.getOrDefault(familyMemberId, List.of());

                // 해당 회원에게 취향 및 행동 데이터가 있는 경우에만 가족 평균에 포함
                boolean hasTasteSignal = memberTaste != null || !memberAnswers.isEmpty() || !memberBucketItems.isEmpty() || !memberReviews.isEmpty();

                if (!hasTasteSignal) {
                    continue;
                }

                TasteSourceDTO.MemberScore memberScore = memberTasteScoreCalculator.calculate(memberTaste, memberAnswers, memberAnswerCategories, memberBucketItems, memberReviews, mission, today);

                memberScores.add(memberScore);
            }

            BigDecimal familyTasteScore = familyTasteScoreCalculator.calculate(memberScores);

            BigDecimal generalScore = generalScoreCalculator.calculate(family.getLatitude(), family.getLongitude(), mission.getPlace().getLatitude(), mission.getPlace().getLongitude(), mission.getCreatedAt(), now);

            BigDecimal finalScore = missionScoreCalculator.calculate(familyTasteScore, generalScore);

            recommendations.add(MissionConverter.toRecommendation(mission, finalScore));
        }

        // 7. 점수 내림차순 정렬
        recommendations.sort(Comparator.comparing(MissionResDTO.Recommendation::score, Comparator.reverseOrder()).thenComparing(MissionResDTO.Recommendation::missionId));

        return recommendations.stream().limit(1).toList();
    }
}
