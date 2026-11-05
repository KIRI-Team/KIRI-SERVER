package com.kiri.server.domain.mission.scheduler;

import com.kiri.server.domain.family.repository.FamilyRepository;
import com.kiri.server.domain.mission.dto.MissionResDTO;
import com.kiri.server.domain.mission.service.FamilyMissionRecommendationService;
import com.kiri.server.domain.mission.service.MissionRecommendationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeeklyMissionRecommendationSchedulerTest {

    @InjectMocks
    private WeeklyMissionRecommendationScheduler scheduler;

    @Mock
    private FamilyRepository familyRepository;

    @Mock
    private MissionRecommendationService missionRecommendationService;

    @Mock
    private FamilyMissionRecommendationService familyMissionRecommendationService;

    @Test
    @DisplayName("모든 가족의 추천 미션을 순차적으로 갱신한다.")
    void refreshWeeklyRecommendationsRefreshesAllFamilies() {
        List<Long> familyIds = List.of(1L, 2L, 3L);
        List<MissionResDTO.Recommendation> recommendations = List.of();

        when(familyRepository.findAllIds()).thenReturn(familyIds);

        when(missionRecommendationService.recommendByFamilyId(anyLong())).thenReturn(recommendations);

        scheduler.refreshWeeklyRecommendations();

        for (Long familyId : familyIds) {
            verify(missionRecommendationService).recommendByFamilyId(familyId);

            verify(familyMissionRecommendationService).saveRecommendations(familyId, recommendations);
        }
    }

    @Test
    @DisplayName("한 가족의 추천 계산에 실패해도 나머지 가족의 추천을 갱신한다.")
    void refreshWeeklyRecommendationsContinuesWhenRecommendationCalculationFails() {
        List<Long> familyIds = List.of(1L, 2L);
        List<MissionResDTO.Recommendation> recommendations = List.of();

        when(familyRepository.findAllIds()).thenReturn(familyIds);

        when(missionRecommendationService.recommendByFamilyId(1L)).thenThrow(new RuntimeException("추천 계산 실패"));

        when(missionRecommendationService.recommendByFamilyId(2L)).thenReturn(recommendations);

        assertDoesNotThrow(() -> scheduler.refreshWeeklyRecommendations());

        verify(missionRecommendationService).recommendByFamilyId(1L);
        verify(missionRecommendationService).recommendByFamilyId(2L);

        verify(familyMissionRecommendationService, never()).saveRecommendations(eq(1L), anyList());

        verify(familyMissionRecommendationService).saveRecommendations(2L, recommendations);
    }

    @Test
    @DisplayName("한 가족의 추천 저장에 실패해도 나머지 가족의 추천을 갱신한다.")
    void refreshWeeklyRecommendationsContinuesWhenSavingFails() {
        List<Long> familyIds = List.of(1L, 2L);
        List<MissionResDTO.Recommendation> recommendations = List.of();

        when(familyRepository.findAllIds()).thenReturn(familyIds);

        when(missionRecommendationService.recommendByFamilyId(anyLong())).thenReturn(recommendations);

        doThrow(new RuntimeException("추천 저장 실패")).when(familyMissionRecommendationService).saveRecommendations(1L, recommendations);

        assertDoesNotThrow(() -> scheduler.refreshWeeklyRecommendations());

        verify(familyMissionRecommendationService).saveRecommendations(1L, recommendations);

        verify(missionRecommendationService).recommendByFamilyId(2L);

        verify(familyMissionRecommendationService).saveRecommendations(2L, recommendations);
    }

    @Test
    @DisplayName("가족이 없으면 추천 갱신을 수행하지 않는다.")
    void refreshWeeklyRecommendationsDoesNothingWhenNoFamiliesExist() {
        when(familyRepository.findAllIds()).thenReturn(List.of());

        scheduler.refreshWeeklyRecommendations();

        verifyNoInteractions(missionRecommendationService, familyMissionRecommendationService);
    }
}