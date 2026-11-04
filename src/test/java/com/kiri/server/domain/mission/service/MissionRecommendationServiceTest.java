package com.kiri.server.domain.mission.service;

import com.kiri.server.domain.bucket.repository.BucketItemRepository;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.family.repository.FamilyRepository;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.dto.MissionResDTO;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.repository.MissionRepository;
import com.kiri.server.domain.question.repository.MemberAnswerRepository;
import com.kiri.server.domain.review.repository.ReviewRepository;
import com.kiri.server.domain.taste.caculator.FamilyTasteScoreCalculator;
import com.kiri.server.domain.taste.caculator.GeneralScoreCalculator;
import com.kiri.server.domain.taste.caculator.MissionScoreCalculator;
import com.kiri.server.domain.taste.repository.MemberTasteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissionRecommendationServiceTest {

    @InjectMocks
    private MissionRecommendationService missionRecommendationService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private MemberTasteRepository memberTasteRepository;

    @Mock
    private MemberAnswerRepository memberAnswerRepository;


    @Mock
    private BucketItemRepository bucketItemRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private FamilyRepository familyRepository;

    @Mock
    private FamilyTasteScoreCalculator familyTasteScoreCalculator;

    @Mock
    private GeneralScoreCalculator generalScoreCalculator;

    @Mock
    private MissionScoreCalculator missionScoreCalculator;

    private Family family;
    private Member member;

    private static final Long FAMILY_ID = 1L;
    private static final Long MEMBER_ID = 1L;

    @BeforeEach
    void setUp() {
        family = mock(Family.class);
        member = mock(Member.class);
    }

    @Test
    @DisplayName("추천 가능한 미션 중 최종 점수가 가장 높은 미션 1개를 반환한다.")
    void recommendByFamilyIdReturnsHighestScoredMissionOnly() {
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family));

        Mission mission1 = createMission(1L, "미션 1", new BigDecimal("37.0"));
        Mission mission2 = createMission(2L, "미션 2", new BigDecimal("38.0"));

        prepareRecommendationData(List.of(mission1, mission2));

        List<MissionResDTO.Recommendation> result = missionRecommendationService.recommendByFamilyId(FAMILY_ID);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).missionId());
        assertEquals(new BigDecimal("0.9"), result.get(0).score());
    }

    @Test
    @DisplayName("추천 점수가 동일하면 미션 ID가 작은 미션을 선정한다.")
    void recommendByFamilyIdReturnsMissionWithSmallerIdWhenScoresAreEqual() {
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family));

        Mission mission1 = createMission(10L, "미션 10", new BigDecimal("37.0"));
        Mission mission2 = createMission(5L, "미션 5", new BigDecimal("37.0"));

        prepareRecommendationData(List.of(mission1, mission2));

        List<MissionResDTO.Recommendation> result = missionRecommendationService.recommendByFamilyId(FAMILY_ID);

        assertEquals(1, result.size());
        assertEquals(5L, result.get(0).missionId());
    }

    @Test
    @DisplayName("가족 구성원이 없으면 빈 추천 목록을 반환한다.")
    void recommendByFamilyIdReturnsEmptyListWhenFamilyHasNoMembers() {
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family));

        when(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).thenReturn(List.of());

        List<MissionResDTO.Recommendation> result = missionRecommendationService.recommendByFamilyId(FAMILY_ID);

        assertTrue(result.isEmpty());

        verify(missionRepository, never()).findAllRecommendableMissions(any(LocalDate.class));
    }

    @Test
    @DisplayName("추천 가능한 미션이 없으면 빈 추천 목록을 반환한다.")
    void recommendByFamilyIdReturnsEmptyListWhenNoRecommendableMissions() {

        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family));

        prepareRecommendationData(List.of());

        List<MissionResDTO.Recommendation> result = missionRecommendationService.recommendByFamilyId(FAMILY_ID);

        assertTrue(result.isEmpty());

        verify(familyTasteScoreCalculator, never()).calculate(anyList());

        verify(missionScoreCalculator, never()).calculate(any(BigDecimal.class), any(BigDecimal.class));
    }

    private void prepareRecommendationData(List<Mission> missions) {
        when(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).thenReturn(List.of(member));

        when(member.getId()).thenReturn(MEMBER_ID);

        when(memberTasteRepository.findAllWithCategoriesByMemberIdIn(anyList())).thenReturn(List.of());

        when(memberAnswerRepository.findAllByMember_IdIn(anyList())).thenReturn(List.of());

        when(bucketItemRepository.findAllByMember_IdIn(anyList())).thenReturn(List.of());

        when(reviewRepository.findAllCompletedReviewsByMemberIds(anyList())).thenReturn(List.of());

        when(missionRepository.findAllRecommendableMissions(any(LocalDate.class))).thenReturn(missions);

        if (missions.isEmpty()) {
            return;
        }

        when(family.getLatitude()).thenReturn(new BigDecimal("37.0"));
        when(family.getLongitude()).thenReturn(new BigDecimal("127.0"));

        when(familyTasteScoreCalculator.calculate(anyList())).thenReturn(BigDecimal.ZERO);

        when(generalScoreCalculator.calculate(any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenAnswer(invocation -> {
            BigDecimal missionLatitude = invocation.getArgument(2);

            if (missionLatitude.compareTo(new BigDecimal("38.0")) == 0) {
                return new BigDecimal("0.9");
            }

            return new BigDecimal("0.4");
        });

        when(missionScoreCalculator.calculate(any(BigDecimal.class), any(BigDecimal.class))).thenAnswer(invocation -> invocation.getArgument(1));
    }

    private Mission createMission(Long missionId, String title,BigDecimal latitude) {
        Mission mission = mock(Mission.class, RETURNS_DEEP_STUBS);

        when(mission.getId()).thenReturn(missionId);
        when(mission.getTitle()).thenReturn(title);
        when(mission.getDescription()).thenReturn("미션 설명");
        when(mission.getImageUrl()).thenReturn("https://example.com/image.png");
        when(mission.getRecommendationReason()).thenReturn("추천 이유");

        when(mission.getPlace().getLatitude()).thenReturn(latitude);
        when(mission.getPlace().getLongitude()).thenReturn(new BigDecimal("127.0"));

        when(mission.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 11, 1, 12, 0));

        return mission;
    }
}