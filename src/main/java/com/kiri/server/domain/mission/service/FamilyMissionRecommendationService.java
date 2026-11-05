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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FamilyMissionRecommendationService {

    private final FamilyMissionRecommendationRepository familyMissionRecommendationRepository;
    private final FamilyRepository familyRepository;
    private final MissionRepository missionRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public void saveRecommendations(Long familyId, List<MissionResDTO.Recommendation> recommendations) {
        Family family = familyRepository.findById(familyId).orElseThrow(() -> new FamilyException(FamilyErrorCode.NOT_FOUND));

        familyMissionRecommendationRepository.deleteAllByFamilyId(familyId);

        if (recommendations == null || recommendations.isEmpty()) {
            return;
        }

        List<MissionResDTO.Recommendation> sortedRecommendations = recommendations.stream().sorted(Comparator.comparing(MissionResDTO.Recommendation::score).reversed().thenComparing(MissionResDTO.Recommendation::missionId)).toList();

        List<Long> missionIds = sortedRecommendations.stream().map(MissionResDTO.Recommendation::missionId).toList();

        Map<Long, Mission> missionMap = missionRepository.findAllById(missionIds).stream().collect(Collectors.toMap(Mission::getId, Function.identity()));

        List<FamilyMissionRecommendation> entities = new java.util.ArrayList<>();

        for (int i = 0; i < sortedRecommendations.size(); i++) {
            MissionResDTO.Recommendation recommendation = sortedRecommendations.get(i);

            Mission mission = missionMap.get(recommendation.missionId());

            if (mission == null) {
                throw new MissionException(MissionErrorCode.NOT_FOUND);
            }

            entities.add(FamilyMissionRecommendationConverter.toFamilyMissionRecommendation(family, mission, recommendation.score(), i + 1));
        }

        familyMissionRecommendationRepository.saveAll(entities);
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
        return familyMissionRecommendationRepository.findFirstByFamilyIdOrderByRecommendationRankAsc(familyId).map(recommendation -> MissionConverter.toRecommendation(recommendation.getMission(), recommendation.getRecommendationScore())).orElse(null);
    }

}
