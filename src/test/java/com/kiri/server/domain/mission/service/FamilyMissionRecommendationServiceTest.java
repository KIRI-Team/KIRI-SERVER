package com.kiri.server.domain.mission.service;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.family.exception.FamilyException;
import com.kiri.server.domain.family.repository.FamilyRepository;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.dto.MissionResDTO;
import com.kiri.server.domain.mission.entity.FamilyMissionRecommendation;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.exception.MissionException;
import com.kiri.server.domain.mission.repository.FamilyMissionRecommendationRepository;
import com.kiri.server.domain.mission.repository.MissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FamilyMissionRecommendationServiceTest {

    @InjectMocks
    private FamilyMissionRecommendationService familyMissionRecommendationService;

    @Mock
    private FamilyMissionRecommendationRepository familyMissionRecommendationRepository;

    @Mock
    private FamilyRepository familyRepository;

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private MemberRepository memberRepository;

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
    @DisplayName("추천 점수가 가장 높은 미션 하나를 가족 추천 미션으로 저장한다.")
    void saveRecommendationsSavesHighestScoreRecommendation() {
        Mission mission2 = createMission(2L);

        MissionResDTO.Recommendation recommendation1 = createRecommendation(1L, "0.5");
        MissionResDTO.Recommendation recommendation2 = createRecommendation(2L, "0.9");
        MissionResDTO.Recommendation recommendation3 = createRecommendation(3L, "0.7");

        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family));
        when(missionRepository.findById(2L)).thenReturn(Optional.of(mission2));

        familyMissionRecommendationService.saveRecommendations(FAMILY_ID, List.of(recommendation1, recommendation2, recommendation3));

        ArgumentCaptor<FamilyMissionRecommendation> captor = ArgumentCaptor.forClass(FamilyMissionRecommendation.class);

        verify(familyMissionRecommendationRepository).save(captor.capture());

        FamilyMissionRecommendation savedRecommendation = captor.getValue();

        assertEquals(2L, savedRecommendation.getMission().getId());
        assertEquals(new BigDecimal("0.9"), savedRecommendation.getRecommendationScore());
        assertEquals(1, savedRecommendation.getRecommendationRank());

        assertNotNull(savedRecommendation.getStartedAt());
        assertNotNull(savedRecommendation.getEndedAt());
        assertEquals(savedRecommendation.getStartedAt().plusWeeks(1), savedRecommendation.getEndedAt());

        verify(familyMissionRecommendationRepository, times(1)).save(any(FamilyMissionRecommendation.class));
    }

    @Test
    @DisplayName("추천 목록이 비어 있으면 기존 추천 데이터를 삭제하고 저장하지 않는다.")
    void saveRecommendationsDeletesExistingRecommendationsWhenListIsEmpty() {
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family));

        familyMissionRecommendationService.saveRecommendations(FAMILY_ID, List.of());

        verify(familyMissionRecommendationRepository, never()).saveAll(anyList());

        verify(missionRepository, never()).findAllById(anyList());
    }

    @Test
    @DisplayName("존재하지 않는 가족이면 추천 데이터를 저장하지 않고 예외를 발생시킨다.")
    void saveRecommendationsThrowsExceptionWhenFamilyNotFound() {
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.empty());

        assertThrows(FamilyException.class, () -> familyMissionRecommendationService.saveRecommendations(FAMILY_ID, List.of(createRecommendation(1L, "0.9"))));

        verify(familyMissionRecommendationRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("추천 대상 미션이 존재하지 않으면 예외를 발생시킨다.")
    void saveRecommendationsThrowsExceptionWhenMissionNotFound() {
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family));

        when(missionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(MissionException.class, () -> familyMissionRecommendationService.saveRecommendations(FAMILY_ID, List.of(createRecommendation(1L, "0.9"))));

        verify(familyMissionRecommendationRepository, never()).save(any(FamilyMissionRecommendation.class));
    }

    @Test
    @DisplayName("회원 ID로 해당 가족의 추천 미션을 조회한다.")
    void getRecommendationsByMemberIdReturnsFamilyRecommendation() {
        Mission mission = createMission(1L);

        FamilyMissionRecommendation recommendation = mock(FamilyMissionRecommendation.class);

        when(memberRepository.findByIdAndDeletedAtIsNull(MEMBER_ID)).thenReturn(Optional.of(member));

        when(member.getFamily()).thenReturn(family);
        when(family.getId()).thenReturn(FAMILY_ID);

        when(familyMissionRecommendationRepository.findFirstByFamilyIdAndStartedAtLessThanEqualAndEndedAtGreaterThanOrderByStartedAtDesc(eq(FAMILY_ID), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(Optional.of(recommendation));

        when(recommendation.getMission()).thenReturn(mission);
        when(recommendation.getRecommendationScore()).thenReturn(new BigDecimal("0.9"));

        MissionResDTO.Recommendation result = familyMissionRecommendationService.getRecommendationsByMemberId(MEMBER_ID);

        assertNotNull(result);
        assertEquals(1L, result.missionId());
        assertEquals(new BigDecimal("0.9"), result.score());
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 예외를 발생시킨다.")
    void getRecommendationsByMemberIdThrowsExceptionWhenMemberNotFound() {
        when(memberRepository.findByIdAndDeletedAtIsNull(MEMBER_ID)).thenReturn(Optional.empty());

        assertThrows(MemberException.class, () -> familyMissionRecommendationService.getRecommendationsByMemberId(MEMBER_ID));

        verify(familyMissionRecommendationRepository, never()).findFirstByFamilyIdAndStartedAtLessThanEqualAndEndedAtGreaterThanOrderByStartedAtDesc(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("가족에 저장된 추천 미션이 없으면 null을 반환한다.")
    void getRecommendationsByMemberIdReturnsNullWhenRecommendationNotFound() {
        when(memberRepository.findByIdAndDeletedAtIsNull(MEMBER_ID)).thenReturn(Optional.of(member));

        when(member.getFamily()).thenReturn(family);
        when(family.getId()).thenReturn(FAMILY_ID);

        when(familyMissionRecommendationRepository.findFirstByFamilyIdAndStartedAtLessThanEqualAndEndedAtGreaterThanOrderByStartedAtDesc(eq(FAMILY_ID), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(Optional.empty());

        MissionResDTO.Recommendation result = familyMissionRecommendationService.getRecommendationsByMemberId(MEMBER_ID);

        assertNull(result);
    }

    private Mission createMission(Long missionId) {
        Mission mission = mock(Mission.class);
        when(mission.getId()).thenReturn(missionId);
        return mission;
    }

    private MissionResDTO.Recommendation createRecommendation(Long missionId, String score) {
        return MissionResDTO.Recommendation.builder()
                .missionId(missionId)
                .score(new BigDecimal(score))
                .build();
    }
}