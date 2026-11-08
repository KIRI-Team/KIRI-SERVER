package com.kiri.server.domain.mission.service;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.family.exception.FamilyException;
import com.kiri.server.domain.family.exception.code.FamilyErrorCode;
import com.kiri.server.domain.family.repository.FamilyRepository;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.converter.FamilyMissionRecommendationConverter;
import com.kiri.server.domain.mission.converter.MissionConverter;
import com.kiri.server.domain.mission.dto.MissionResDTO;
import com.kiri.server.domain.mission.entity.FamilyMissionRecommendation;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.exception.MissionException;
import com.kiri.server.domain.mission.exception.code.MissionErrorCode;
import com.kiri.server.domain.mission.repository.FamilyMissionRecommendationRepository;
import com.kiri.server.domain.mission.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FamilyMissionRecommendationService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final FamilyMissionRecommendationRepository familyMissionRecommendationRepository;
    private final FamilyRepository familyRepository;
    private final MissionRepository missionRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public void saveRecommendations(Long familyId, List<MissionResDTO.Recommendation> recommendations) {
        Family family = familyRepository.findById(familyId).orElseThrow(() -> new FamilyException(FamilyErrorCode.NOT_FOUND));

        if (recommendations == null || recommendations.isEmpty()) {
            return;
        }

        LocalDateTime startedAt = LocalDate.now(SEOUL_ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime endedAt = startedAt.plusWeeks(1);

        if (familyMissionRecommendationRepository.existsByFamilyIdAndStartedAt(familyId, startedAt)) {
            return;
        }

        MissionResDTO.Recommendation recommendation = recommendations.stream().min(Comparator.comparing(MissionResDTO.Recommendation::score, Comparator.reverseOrder()).thenComparing(MissionResDTO.Recommendation::missionId)).orElseThrow();

        Mission mission = missionRepository.findById(recommendation.missionId()).orElseThrow(() -> new MissionException(MissionErrorCode.NOT_FOUND));

        FamilyMissionRecommendation entity = FamilyMissionRecommendationConverter.toFamilyMissionRecommendation(family, mission, recommendation.score(), 1, startedAt, endedAt);

        familyMissionRecommendationRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public MissionResDTO.Recommendation getRecommendationsByMemberId(Long memberId) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId).orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));

        Family family = member.getFamily();

        if (family == null) {
            throw new FamilyException(FamilyErrorCode.NOT_FOUND);
        }

        return getRecommendationsByFamilyId(family.getId());
    }

    private MissionResDTO.Recommendation getRecommendationsByFamilyId(Long familyId) {
        LocalDateTime now = LocalDateTime.now(SEOUL_ZONE);

        return familyMissionRecommendationRepository.findFirstByFamilyIdAndStartedAtLessThanEqualAndEndedAtGreaterThanOrderByStartedAtDesc(familyId, now, now).map(recommendation -> MissionConverter.toRecommendation(recommendation.getMission(), recommendation.getRecommendationScore())).orElse(null);
    }

}
