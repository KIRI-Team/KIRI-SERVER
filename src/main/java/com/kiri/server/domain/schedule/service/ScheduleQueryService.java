package com.kiri.server.domain.schedule.service;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.mission.repository.MissionRepository;
import com.kiri.server.domain.place.entity.Place;
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
import com.kiri.server.domain.taste.dto.TasteProfileDTO;
import com.kiri.server.domain.taste.service.TasteProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
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
    private static final double NEARBY_DISTANCE_KM = 5.0;
    private static final double KM_PER_DEGREE = 111.0;
    private static final double EARTH_RADIUS_KM = 6371.0;
    // TODO: 취향 테스트 PriceLevel.LOW 기준이 정해지면 교체
    private static final int LOW_PRICE = 10_000;

    private final ScheduleRepository scheduleRepository;
    private final ScheduleParticipantRepository scheduleParticipantRepository;
    private final ScheduleConditionRepository scheduleConditionRepository;
    private final MemberRepository memberRepository;
    private final MissionRepository missionRepository;
    private final TasteProfileService tasteProfileService;
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

        List<ScheduleResDTO.CalendarItem> items = schedules.stream()
                .map(schedule -> ScheduleConverter.toCalendarItem(schedule, myParticipants.get(schedule.getId())))
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

        List<Mission> candidates = findNearbyMissions(base, schedule.getScheduleDate());
        if (candidates.isEmpty()) {
            return List.of();
        }

        ToDoubleFunction<Mission> tasteScore = participantTasteScore(schedule, family.getId());
        Set<ConditionType> conditions = scheduleConditionRepository.findBySchedule_Id(scheduleId).stream()
                .map(ScheduleCondition::getConditionType)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ConditionType.class)));
        return recommendPlaces(candidates, base, tasteScore, conditions);
    }

    // 반경 내에서 일정 날짜에 운영 중인 활성 미션 (메인 미션 장소 제외)
    private List<Mission> findNearbyMissions(Place base, LocalDate scheduleDate) {
        double latitude = base.getLatitude().doubleValue();
        double longitude = base.getLongitude().doubleValue();
        double latitudeDelta = NEARBY_DISTANCE_KM / KM_PER_DEGREE;
        double longitudeDelta = NEARBY_DISTANCE_KM / (KM_PER_DEGREE * Math.cos(Math.toRadians(latitude)));

        List<Long> ids = missionRepository.findNearbyIds(latitude, longitude,
                latitude - latitudeDelta, latitude + latitudeDelta,
                longitude - longitudeDelta, longitude + longitudeDelta,
                NEARBY_DISTANCE_KM, base.getId(), scheduleDate);
        return ids.isEmpty() ? List.of() : missionRepository.findAllWithPlaceByIdIn(ids);
    }

    // 거절하지 않은 일정 참여자가 선호하는 비율(0~1)을 미션 점수로 바꾸는 함수
    // TODO: 미션 추천 점수 공식이 구현되면 공용 취향 점수 함수로 교체
    private ToDoubleFunction<Mission> participantTasteScore(Schedule schedule, Long familyId) {
        Set<Long> participantIds = scheduleParticipantRepository.findBySchedule_Id(schedule.getId()).stream()
                .filter(participant -> participant.getMember() != null
                        && participant.getStatus() != ParticipantStatus.DECLINED)
                .map(participant -> participant.getMember().getId())
                .collect(Collectors.toSet());

        List<TasteProfileDTO.MemberProfile> profiles = tasteProfileService.getFamilyProfile(familyId).members()
                .stream()
                .filter(profile -> participantIds.contains(profile.memberId()))
                .filter(profile -> profile.isSet() && profile.categories() != null)
                .toList();
        if (profiles.isEmpty()) {
            return mission -> 0;
        }

        Map<Category, Long> counts = profiles.stream()
                .flatMap(profile -> profile.categories().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return mission -> (double) counts.getOrDefault(mission.getCategory(), 0L) / profiles.size();
    }

    // 취향 + 컨디션 점수 높은 순 → 같으면 가까운 순으로 정렬, 장소별 첫 미션만 남겨 장소 추천
    private List<ScheduleResDTO.NearbyItem> recommendPlaces(List<Mission> candidates, Place base,
                                                            ToDoubleFunction<Mission> tasteScore,
                                                            Set<ConditionType> conditions) {
        Map<Long, Double> distances = candidates.stream()
                .map(Mission::getPlace)
                .distinct()
                .collect(Collectors.toMap(Place::getId, place -> distanceKm(base, place)));
        ToDoubleFunction<Mission> distance = mission -> distances.get(mission.getPlace().getId());
        ToDoubleFunction<Mission> score = mission -> tasteScore.applyAsDouble(mission)
                + conditionScore(mission, distance.applyAsDouble(mission), conditions);

        Comparator<Mission> order = Comparator.comparingDouble(score).reversed()
                .thenComparingDouble(distance);

        Map<Long, Mission> representatives = new LinkedHashMap<>();
        candidates.stream()
                .sorted(order)
                .forEach(mission -> representatives.putIfAbsent(mission.getPlace().getId(), mission));

        // TODO: 추천 개수(상위 N개) 제한이 정해지면 limit 추가
        return representatives.values().stream()
                .map(ScheduleConverter::toNearbyItem)
                .toList();
    }

    // 조건마다 0~1 가산점(예산은 요금 정보가 없으면 감점)을 더한다
    private double conditionScore(Mission mission, double distanceKm, Set<ConditionType> conditions) {
        double score = 0;
        if (conditions.contains(ConditionType.REDUCE_STEPS)) {
            score += 1 - distanceKm / NEARBY_DISTANCE_KM;
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

    private double budgetScore(Integer price) {
        if (price == null) {
            return -1;
        }
        if (price == 0) {
            return 1;
        }
        return price <= LOW_PRICE ? 0.5 : 0;
    }

    private double distanceKm(Place base, Place place) {
        double phi1 = Math.toRadians(base.getLatitude().doubleValue());
        double phi2 = Math.toRadians(place.getLatitude().doubleValue());
        double deltaPhi = phi2 - phi1;
        double deltaLambda = Math.toRadians(place.getLongitude().doubleValue() - base.getLongitude().doubleValue());
        double a = Math.pow(Math.sin(deltaPhi / 2), 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.pow(Math.sin(deltaLambda / 2), 2);
        double km = 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
        return Math.round(km * 10) / 10.0;
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
}
