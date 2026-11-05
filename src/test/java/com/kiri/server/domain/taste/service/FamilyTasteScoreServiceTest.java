package com.kiri.server.domain.taste.service;

import com.kiri.server.domain.bucket.repository.BucketItemRepository;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.question.repository.MemberAnswerCategoryRepository;
import com.kiri.server.domain.question.repository.MemberAnswerRepository;
import com.kiri.server.domain.review.repository.ReviewRepository;
import com.kiri.server.domain.taste.caculator.FamilyTasteScoreCalculator;
import com.kiri.server.domain.taste.caculator.MemberTasteScoreCalculator;
import com.kiri.server.domain.taste.dto.TasteSourceDTO;
import com.kiri.server.domain.taste.entity.MemberTaste;
import com.kiri.server.domain.taste.repository.MemberTasteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FamilyTasteScoreServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    @Mock
    private MemberTasteRepository memberTasteRepository;

    @Mock
    private MemberAnswerRepository memberAnswerRepository;

    @Mock
    private MemberAnswerCategoryRepository memberAnswerCategoryRepository;

    @Mock
    private BucketItemRepository bucketItemRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private MemberTasteScoreCalculator memberTasteScoreCalculator;

    private FamilyTasteScoreService familyTasteScoreService;

    @BeforeEach
    void setUp() {
        familyTasteScoreService = new FamilyTasteScoreService(memberTasteRepository, memberAnswerRepository,
                memberAnswerCategoryRepository, bucketItemRepository, reviewRepository, memberTasteScoreCalculator,
                new FamilyTasteScoreCalculator());
    }

    @Test
    @DisplayName("취향·활동 데이터가 없는 회원은 평균에서 빼고 미션별 취향 점수를 계산한다.")
    void calculate_excludesMembersWithoutSignal() {
        Mission mission = Mission.builder().id(100L).build();
        MemberTaste taste = MemberTaste.builder().member(Member.builder().id(1L).build()).build();
        List<Long> memberIds = List.of(1L, 2L);
        given(memberTasteRepository.findAllWithCategoriesByMemberIdIn(memberIds)).willReturn(List.of(taste));
        given(memberAnswerRepository.findAllByMember_IdIn(memberIds)).willReturn(List.of());
        given(bucketItemRepository.findAllByMember_IdIn(memberIds)).willReturn(List.of());
        given(reviewRepository.findAllCompletedReviewsByMemberIds(memberIds)).willReturn(List.of());
        given(memberTasteScoreCalculator.calculate(eq(taste), anyList(), anyList(), anyList(), anyList(),
                eq(mission), eq(TODAY))).willReturn(score("0.6"));

        Map<Long, BigDecimal> result = familyTasteScoreService.calculate(memberIds, List.of(mission), TODAY);
        
        assertThat(result.get(100L)).isEqualByComparingTo("0.6");
    }

    @Test
    @DisplayName("대상 회원이 없으면 취향 데이터를 조회하지 않고 빈 결과를 반환한다.")
    void calculate_noMembers() {
        Map<Long, BigDecimal> result = familyTasteScoreService.calculate(
                List.of(), List.of(Mission.builder().id(100L).build()), TODAY);

        assertThat(result).isEmpty();
        verifyNoInteractions(memberTasteRepository, memberAnswerRepository, bucketItemRepository, reviewRepository);
    }

    private TasteSourceDTO.MemberScore score(String total) {
        BigDecimal zero = BigDecimal.ZERO;
        return new TasteSourceDTO.MemberScore(zero, zero, zero, zero, zero, new BigDecimal(total));
    }
}
