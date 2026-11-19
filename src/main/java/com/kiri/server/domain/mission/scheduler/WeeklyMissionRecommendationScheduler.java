package com.kiri.server.domain.mission.scheduler;

import com.kiri.server.domain.family.repository.FamilyRepository;
import com.kiri.server.domain.mission.dto.MissionResDTO;
import com.kiri.server.domain.mission.service.FamilyMissionRecommendationService;
import com.kiri.server.domain.mission.service.MissionRecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyMissionRecommendationScheduler {

    private final FamilyRepository familyRepository;
    private final MissionRecommendationService missionRecommendationService;
    private final FamilyMissionRecommendationService familyMissionRecommendationService;

    @Scheduled(cron = "0 5 0 * * MON", zone = "Asia/Seoul")
    public void refreshWeeklyRecommendations() {

        List<Long> familyIds = familyRepository.findAllIds();

        log.info("주간 미션 추천 갱신 시작. 대상 가족 수: {}", familyIds.size());

        for (Long familyId : familyIds) {
            try {
                List<MissionResDTO.Recommendation> recommendations = missionRecommendationService.recommendByFamilyId(familyId);

                familyMissionRecommendationService.saveRecommendations(familyId, recommendations);

                log.info("가족 ID {} 추천 갱신 완료. 추천 미션 수: {}", familyId, recommendations.size());

            } catch (Exception e) {
                log.error("가족 ID {} 추천 갱신 실패", familyId, e);
            }
        }

        log.info("주간 미션 추천 갱신 종료");
    }
}
