package com.kiri.server.domain.schedule.service;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.schedule.converter.ScheduleConverter;
import com.kiri.server.domain.schedule.dto.ScheduleReqDTO;
import com.kiri.server.domain.schedule.dto.ScheduleResDTO;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.domain.schedule.enums.UpcomingCardType;
import com.kiri.server.domain.schedule.exception.ScheduleException;
import com.kiri.server.domain.schedule.exception.code.ScheduleErrorCode;
import com.kiri.server.domain.schedule.repository.ScheduleParticipantRepository;
import com.kiri.server.domain.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleQueryService {

    private static final int UPCOMING_DAYS = 7;

    private final ScheduleRepository scheduleRepository;
    private final ScheduleParticipantRepository scheduleParticipantRepository;
    private final MemberRepository memberRepository;
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
        List<ScheduleParticipant> participants = scheduleParticipantRepository.findAllWithMemberByScheduleId(scheduleId);

        return ScheduleConverter.toDetail(schedule, participants, LocalDate.now(clock));
    }
}
