package com.kiri.server.domain.schedule.service;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.image.service.ImageUrlService;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.mission.service.NearbyMissionFinder;
import com.kiri.server.domain.place.entity.Place;
import com.kiri.server.domain.review.entity.ReviewPhoto;
import com.kiri.server.domain.review.policy.RepresentativePhotoPolicy;
import com.kiri.server.domain.review.repository.ReviewPhotoRepository;
import com.kiri.server.domain.schedule.converter.ScheduleConverter;
import com.kiri.server.domain.schedule.dto.ScheduleReqDTO;
import com.kiri.server.domain.schedule.dto.ScheduleResDTO;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleCondition;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.ConditionType;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.domain.schedule.enums.UpcomingCardType;
import com.kiri.server.domain.schedule.exception.ScheduleException;
import com.kiri.server.domain.schedule.exception.code.ScheduleErrorCode;
import com.kiri.server.domain.schedule.repository.ScheduleConditionRepository;
import com.kiri.server.domain.schedule.repository.ScheduleParticipantRepository;
import com.kiri.server.domain.schedule.repository.ScheduleRepository;
import com.kiri.server.domain.taste.service.FamilyTasteScoreService;
import com.kiri.server.domain.taste.service.TasteProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleQueryService {

    private static final int UPCOMING_DAYS = 7;
    private static final String TASTE_REASON = "우리 가족이 좋아하는 활동이에요";
    private static final String REST_REASON = "잠깐 쉬어가기 좋은 곳이에요";
    private static final String NEAR_REASON = "일정 장소에서 가까운 곳이에요";
    private static final int LOW_PRICE = 10_000;

    private final ScheduleRepository scheduleRepository;
    private final ScheduleParticipantRepository scheduleParticipantRepository;
    private final ScheduleConditionRepository scheduleConditionRepository;
    private final MemberRepository memberRepository;
    private final NearbyMissionFinder nearbyMissionFinder;
    private final FamilyTasteScoreService familyTasteScoreService;
    private final TasteProfileService tasteProfileService;
    private final ReviewPhotoRepository reviewPhotoRepository;
    private final ImageUrlService imageUrlService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ScheduleResDTO.Calendar getCalendar(Long memberId, ScheduleReqDTO.CalendarCondition condition) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));
        Family family = member.getFamily();
        if (family == null) {
            throw new ScheduleException(ScheduleErrorCode.FAMILY_REQUIRED);
        }

        YearMonth yearMonth = YearMonth.of(condition.year(), condition.month());
        List<Schedule> schedules = scheduleRepository
                .findByFamily_IdAndScheduleDateBetweenOrderByScheduleDateAscScheduleTimeAsc(
                        family.getId(), yearMonth.atDay(1), yearMonth.atEndOfMonth());
        if (schedules.isEmpty()) {
            return ScheduleConverter.toCalendar(condition.year(), condition.month(), List.of());
        }

        List<Long> scheduleIds = schedules.stream().map(Schedule::getId).toList();
        Map<Long, ScheduleParticipant> myParticipants = scheduleParticipantRepository
                .findBySchedule_IdInAndMember_Id(scheduleIds, memberId).stream()
                .collect(Collectors.toMap(participant -> participant.getSchedule().getId(), Function.identity()));

        Map<Long, List<ReviewPhoto>> photosBySchedule = reviewPhotoRepository.findAllByScheduleIds(scheduleIds).stream()
                .collect(Collectors.groupingBy(photo -> photo.getReview().getSchedule().getId()));

        List<ScheduleResDTO.CalendarItem> items = schedules.stream()
                .map(schedule -> {
                    ReviewPhoto representative = RepresentativePhotoPolicy.select(
                            schedule.getRepresentativeReviewPhotoId(),
                            photosBySchedule.getOrDefault(schedule.getId(), List.of()),
                            ReviewPhoto::getId);
                    String reviewImageUrl = representative == null
                            ? null : imageUrlService.toUrl(representative.getImageKey());
                    return ScheduleConverter.toCalendarItem(
                            schedule, myParticipants.get(schedule.getId()), reviewImageUrl);
                })
                .toList();

        return ScheduleConverter.toCalendar(condition.year(), condition.month(), items);
    }

    @Transactional(readOnly = true)
    public ScheduleResDTO.UpcomingHome getUpcoming(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));
        Family family = member.getFamily();
        if (family == null) {
            throw new ScheduleException(ScheduleErrorCode.FAMILY_REQUIRED);
        }

        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);

        Schedule responseRequired = scheduleRepository.findResponseRequired(
                        family.getId(), memberId, ScheduleStatus.ACTIVE_STATUSES, ParticipantStatus.PENDING, now)
                .stream().findFirst().orElse(null);

        Schedule upcoming = scheduleRepository.findUpcomingConfirmed(
                        family.getId(), memberId, ScheduleStatus.CONFIRMED, ParticipantStatus.ACCEPTED,
                        today, today.plusDays(UPCOMING_DAYS))
                .stream().findFirst().orElse(null);

        Map<Long, ScheduleParticipant> myParticipants = findMyParticipants(responseRequired, upcoming, memberId);

        ScheduleResDTO.UpcomingCard responseRequiredCard = responseRequired == null ? null
                : ScheduleConverter.toUpcomingCard(responseRequired, myParticipants.get(responseRequired.getId()),
                        UpcomingCardType.RESPONSE_REQUIRED, today);

        ScheduleResDTO.UpcomingCard upcomingCard = upcoming == null ? null
                : ScheduleConverter.toUpcomingCard(upcoming, myParticipants.get(upcoming.getId()),
                        upcoming.getScheduleDate().isEqual(today) ? UpcomingCardType.TODAY : UpcomingCardType.UPCOMING,
                        today);

        return ScheduleConverter.toUpcomingHome(responseRequiredCard, upcomingCard);
    }

    @Transactional(readOnly = true)
    public ScheduleResDTO.Conditions getConditions(Long memberId, Long scheduleId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));
        Family family = member.getFamily();
        if (family == null) {
            throw new ScheduleException(ScheduleErrorCode.FAMILY_REQUIRED);
        }

        Schedule schedule = scheduleRepository.findWithMissionByIdAndFamilyId(scheduleId, family.getId())
                .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        if (!scheduleParticipantRepository.existsBySchedule_IdAndMember_IdAndStatusAndReconfirmRequiredFalse(
                scheduleId, memberId, ParticipantStatus.ACCEPTED)) {
            throw new ScheduleException(ScheduleErrorCode.NOT_FINAL_PARTICIPANT);
        }
        if (schedule.getStatus() != ScheduleStatus.CONFIRMED
                || !schedule.getScheduleDate().isEqual(LocalDate.now(clock))) {
            throw new ScheduleException(ScheduleErrorCode.CONDITION_NOT_VIEWABLE);
        }

        List<ConditionType> conditions = scheduleConditionRepository.findBySchedule_Id(scheduleId).stream()
                .map(ScheduleCondition::getConditionType)
                .sorted()
                .toList();

        return ScheduleConverter.toConditions(schedule, conditions);
    }

    @Transactional(readOnly = true)
    public List<ScheduleResDTO.NearbyItem> getNearby(Long memberId, Long scheduleId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));
        Family family = member.getFamily();
        if (family == null) {
            throw new ScheduleException(ScheduleErrorCode.FAMILY_REQUIRED);
        }

        Schedule schedule = scheduleRepository.findWithMissionByIdAndFamilyId(scheduleId, family.getId())
                .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        Place base = schedule.getBucketItem().getMission().getPlace();

        List<Mission> candidates = nearbyMissionFinder.findCandidates(base, schedule.getScheduleDate());
        if (candidates.isEmpty()) {
            return List.of();
        }

        Set<Long> tasteMemberIds = tasteMemberIds(schedule);
        ToDoubleFunction<Mission> tasteScore = participantTasteScore(tasteMemberIds, candidates);
        Set<Category> likedCategories = likedCategories(family.getId(), tasteMemberIds);
        Set<ConditionType> conditions = scheduleConditionRepository.findBySchedule_Id(scheduleId).stream()
                .map(ScheduleCondition::getConditionType)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ConditionType.class)));

        return nearbyMissionFinder.pickTop(candidates, base,
                        candidate -> tasteScore.applyAsDouble(candidate.mission())
                                + conditionScore(candidate.mission(), candidate.distanceKm(), conditions))
                .stream()
                .map(candidate -> ScheduleConverter.toNearbyItem(candidate.mission(),
                        nearbyReason(candidate.mission(), candidate.distanceKm(), likedCategories, conditions)))
                .toList();
    }

    // 제안자와 수락한 참여자의 취향으로 계산한 미션별 취향 점수
    private ToDoubleFunction<Mission> participantTasteScore(Set<Long> tasteMemberIds, List<Mission> candidates) {
        Map<Long, BigDecimal> scores = familyTasteScoreService.calculate(
                tasteMemberIds, candidates, LocalDate.now(clock));
        return mission -> scores.getOrDefault(mission.getId(), BigDecimal.ZERO).doubleValue();
    }

    // 제안자·수락자가 취향 테스트에서 직접 고른 카테고리 (카드 문구의 "가족이 좋아하는 활동" 근거)
    private Set<Category> likedCategories(Long familyId, Set<Long> tasteMemberIds) {
        if (tasteMemberIds.isEmpty()) {
            return Set.of();
        }
        return tasteProfileService.getFamilyProfile(familyId).members().stream()
                .filter(profile -> tasteMemberIds.contains(profile.memberId()))
                .filter(profile -> profile.isSet() && profile.categories() != null)
                .flatMap(profile -> profile.categories().stream())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Category.class)));
    }

    private Set<Long> tasteMemberIds(Schedule schedule) {
        Set<Long> memberIds = scheduleParticipantRepository.findBySchedule_Id(schedule.getId()).stream()
                .filter(participant -> participant.getMember() != null
                        && participant.getStatus() == ParticipantStatus.ACCEPTED
                        && !Boolean.TRUE.equals(participant.getReconfirmRequired()))
                .map(participant -> participant.getMember().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (schedule.getProposer() != null) {
            memberIds.add(schedule.getProposer().getId());
        }
        return memberIds;
    }

    // 실제로 확인된 근거 (취향 일치 → 휴식 조건 → 가까운 거리)
    // 근거가 없으면 미션 소개 문구
    private String nearbyReason(Mission mission, double distanceKm, Set<Category> likedCategories,
                                Set<ConditionType> conditions) {
        if (likedCategories.contains(mission.getCategory())) {
            return TASTE_REASON;
        }
        if (conditions.contains(ConditionType.MORE_REST) && restScore(mission) == 1) {
            return REST_REASON;
        }
        if (distanceKm <= NearbyMissionFinder.NEAR_DISTANCE_KM) {
            return NEAR_REASON;
        }
        return mission.getDescription();
    }

    // 조건마다 0~1 가산점을 더한다
    private double conditionScore(Mission mission, double distanceKm, Set<ConditionType> conditions) {
        double score = 0;
        if (conditions.contains(ConditionType.REDUCE_STEPS)) {
            score += 1 - distanceKm / NearbyMissionFinder.RADIUS_KM;
        }
        if (conditions.contains(ConditionType.MORE_REST)) {
            score += restScore(mission);
        }
        if (conditions.contains(ConditionType.LOWER_BUDGET)) {
            score += budgetScore(mission.getPrice());
        }
        // TODO: AVOID_CROWD는 혼잡 데이터가 생기면 반영
        return score;
    }

    private double restScore(Mission mission) {
        if (mission.getCategory() == Category.FOOD_CAFE || mission.getIndoorOutdoor() == IndoorOutdoor.INDOOR) {
            return 1;
        }
        return mission.getIndoorOutdoor() == IndoorOutdoor.BOTH ? 0.5 : 0;
    }

    // 1인 기준 무료 1점 / 1만 원 이하 0.5점 / 초과 0점 / 정보 없음 0점
    private double budgetScore(Integer price) {
        if (price == null) {
            return 0;
        }
        if (price == 0) {
            return 1;
        }
        return price <= LOW_PRICE ? 0.5 : 0;
    }

    private Map<Long, ScheduleParticipant> findMyParticipants(Schedule responseRequired, Schedule upcoming,
                                                              Long memberId) {
        List<Long> ids = new ArrayList<>();
        if (responseRequired != null) {
            ids.add(responseRequired.getId());
        }
        if (upcoming != null && (responseRequired == null || !upcoming.getId().equals(responseRequired.getId()))) {
            ids.add(upcoming.getId());
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return scheduleParticipantRepository.findBySchedule_IdInAndMember_Id(ids, memberId).stream()
                .collect(Collectors.toMap(participant -> participant.getSchedule().getId(), Function.identity()));
    }

    @Transactional(readOnly = true)
    public ScheduleResDTO.Detail getDetail(Long memberId, Long scheduleId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));
        Family family = member.getFamily();
        if (family == null) {
            throw new ScheduleException(ScheduleErrorCode.FAMILY_REQUIRED);
        }

        Schedule schedule = scheduleRepository.findById(scheduleId)
                .filter(found -> found.getFamily().getId().equals(family.getId()))
                .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        if (schedule.getStatus() == ScheduleStatus.CANCELLED) {
            throw new ScheduleException(ScheduleErrorCode.SCHEDULE_CANCELLED);
        }
        List<ScheduleParticipant> participants = scheduleParticipantRepository.findAllWithMemberByScheduleId(scheduleId);

        return ScheduleConverter.toDetail(schedule, participants, LocalDate.now(clock));
    }
}
