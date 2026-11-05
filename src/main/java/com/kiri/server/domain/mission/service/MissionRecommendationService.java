package com.kiri.server.domain.mission.service;

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
import com.kiri.server.domain.taste.caculator.GeneralScoreCalculator;
import com.kiri.server.domain.taste.caculator.MissionScoreCalculator;
import com.kiri.server.domain.taste.service.FamilyTasteScoreService;
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

@Service
@RequiredArgsConstructor
public class MissionRecommendationService {

    private final MemberRepository memberRepository;
    private final MissionRepository missionRepository;

    private final FamilyRepository familyRepository;

    private final FamilyTasteScoreService familyTasteScoreService;
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

        // 3. 추천 가능한 미션 조회
        List<Mission> missions = missionRepository.findAllRecommendableMissions(today);

        if (missions.isEmpty()) {
            return List.of();
        }

        // 4. 미션별 가족 취향 점수
        Map<Long, BigDecimal> familyTasteScores = familyTasteScoreService.calculate(memberIds, missions, today);

        // 5. 미션별 최종 점수 계산
        List<MissionResDTO.Recommendation> recommendations = new ArrayList<>();

        for (Mission mission : missions) {

            BigDecimal familyTasteScore = familyTasteScores.getOrDefault(mission.getId(), BigDecimal.ZERO);

            BigDecimal generalScore = generalScoreCalculator.calculate(family.getLatitude(), family.getLongitude(), mission.getPlace().getLatitude(), mission.getPlace().getLongitude(), mission.getCreatedAt(), now);

            BigDecimal finalScore = missionScoreCalculator.calculate(familyTasteScore, generalScore);

            recommendations.add(MissionConverter.toRecommendation(mission, finalScore));
        }

        // 6. 점수 내림차순 정렬
        recommendations.sort(Comparator.comparing(MissionResDTO.Recommendation::score, Comparator.reverseOrder()).thenComparing(MissionResDTO.Recommendation::missionId));

        return recommendations.stream().limit(1).toList();
    }
}
