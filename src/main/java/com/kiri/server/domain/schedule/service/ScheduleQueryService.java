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
import com.kiri.server.domain.schedule.exception.ScheduleException;
import com.kiri.server.domain.schedule.exception.code.ScheduleErrorCode;
import com.kiri.server.domain.schedule.repository.ScheduleParticipantRepository;
import com.kiri.server.domain.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleQueryService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleParticipantRepository scheduleParticipantRepository;
    private final MemberRepository memberRepository;

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
}
