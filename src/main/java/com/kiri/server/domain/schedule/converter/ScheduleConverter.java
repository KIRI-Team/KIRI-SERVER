package com.kiri.server.domain.schedule.converter;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.enums.FamilyRole;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.schedule.dto.ScheduleResDTO;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleCondition;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.CalendarMyStatus;
import com.kiri.server.domain.schedule.enums.ConditionType;
import com.kiri.server.domain.schedule.enums.ScheduleDetailStatus;
import com.kiri.server.domain.schedule.enums.UpcomingCardType;
import com.kiri.server.domain.schedule.exception.ScheduleException;
import com.kiri.server.domain.schedule.exception.code.ScheduleErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

public class ScheduleConverter {

    public static ScheduleCondition toScheduleCondition(Schedule schedule, ConditionType conditionType) {
        return ScheduleCondition.builder()
                .schedule(schedule)
                .conditionType(conditionType)
                .build();
    }

    public static ScheduleResDTO.Created toCreated(Schedule schedule, List<Long> participantMemberIds) {
        return ScheduleResDTO.Created.builder()
                .scheduleId(schedule.getId())
                .bucketItemId(schedule.getBucketItem() == null ? null : schedule.getBucketItem().getId())
                .scheduleDate(schedule.getScheduleDate())
                .scheduleTime(schedule.getScheduleTime())
                .status(schedule.getStatus())
                .responseDeadline(schedule.getResponseDeadline())
                .proposerId(schedule.getProposer() == null ? null : schedule.getProposer().getId())
                .participantMemberIds(participantMemberIds)
                .build();
    }

    public static ScheduleResDTO.Responded toResponded(Schedule schedule, ScheduleParticipant participant) {
        return ScheduleResDTO.Responded.builder()
                .scheduleId(schedule.getId())
                .myStatus(participant.getStatus())
                .status(schedule.getStatus())
                .build();
    }

    public static ScheduleResDTO.Cancelled toCancelled(Schedule schedule) {
        return ScheduleResDTO.Cancelled.builder()
                .scheduleId(schedule.getId())
                .status(schedule.getStatus())
                .bucketItemId(schedule.getBucketItem() == null ? null : schedule.getBucketItem().getId())
                .build();
    }

    public static ScheduleResDTO.Updated toUpdated(Schedule schedule, List<Long> participantMemberIds) {
        return ScheduleResDTO.Updated.builder()
                .scheduleId(schedule.getId())
                .scheduleDate(schedule.getScheduleDate())
                .scheduleTime(schedule.getScheduleTime())
                .status(schedule.getStatus())
                .responseDeadline(schedule.getResponseDeadline())
                .proposerId(schedule.getProposer() == null ? null : schedule.getProposer().getId())
                .participantMemberIds(participantMemberIds)
                .build();
    }

    public static ScheduleResDTO.Calendar toCalendar(int year, int month, List<ScheduleResDTO.CalendarItem> schedules) {
        return ScheduleResDTO.Calendar.builder()
                .year(year)
                .month(month)
                .schedules(schedules)
                .build();
    }

    public static ScheduleResDTO.CalendarItem toCalendarItem(
            Schedule schedule, ScheduleParticipant myParticipant, String reviewImageUrl) {
        return ScheduleResDTO.CalendarItem.builder()
                .scheduleId(schedule.getId())
                .date(schedule.getScheduleDate())
                .time(schedule.getScheduleTime())
                .status(schedule.getStatus())
                .myStatus(toMyStatus(myParticipant))
                .reviewImageUrl(reviewImageUrl)
                .build();
    }

    public static ScheduleResDTO.UpcomingCard toUpcomingCard(Schedule schedule, ScheduleParticipant myParticipant,
                                                             UpcomingCardType type, LocalDate today) {
        return ScheduleResDTO.UpcomingCard.builder()
                .scheduleId(schedule.getId())
                .date(schedule.getScheduleDate())
                .time(schedule.getScheduleTime())
                .status(schedule.getStatus())
                .myStatus(toMyStatus(myParticipant))
                .dDay((int) ChronoUnit.DAYS.between(today, schedule.getScheduleDate()))
                .type(type)
                .title(schedule.getBucketItem() == null ? null : schedule.getBucketItem().getMission().getTitle())
                .build();
    }

    public static ScheduleResDTO.Conditions toConditions(Schedule schedule, List<ConditionType> conditions) {
        Mission mission = schedule.getBucketItem().getMission();
        return ScheduleResDTO.Conditions.builder()
                .missionTitle(mission.getTitle())
                .walkingLevel(mission.getWalkingLevel())
                .durationMinMinutes(mission.getDurationMinMinutes())
                .durationMaxMinutes(mission.getDurationMaxMinutes())
                .conditions(conditions)
                .build();
    }

    public static ScheduleResDTO.UpcomingHome toUpcomingHome(ScheduleResDTO.UpcomingCard responseRequired,
                                                             ScheduleResDTO.UpcomingCard upcoming) {
        return ScheduleResDTO.UpcomingHome.builder()
                .responseRequired(responseRequired)
                .upcoming(upcoming)
                .build();
    }

    public static ScheduleResDTO.Detail toDetail(Schedule schedule, List<ScheduleParticipant> participants,
                                                 Map<FamilyRole, String> characterImageUrls,
                                                 Long memberId, LocalDateTime now) {
        ScheduleParticipant myParticipant = participants.stream()
                .filter(participant -> participant.getMember() != null
                        && memberId.equals(participant.getMember().getId()))
                .findFirst()
                .orElse(null);
        boolean isProposer = schedule.isProposer(memberId);

        return ScheduleResDTO.Detail.builder()
                .missionId(resolveMissionId(schedule))
                .date(schedule.getScheduleDate())
                .time(schedule.getScheduleTime())
                .status(toDetailStatus(schedule, now.toLocalDate()))
                .proposer(toProposer(schedule.getProposer()))
                .requestMessage(schedule.getRequestMessage())
                .myStatus(toMyStatus(myParticipant))
                .canRespond(myParticipant != null && !isProposer && schedule.isResponseOpen(now))
                .canEdit(isProposer && schedule.isUpdatable() && !schedule.hasStarted(now))
                .canCancel(isProposer && schedule.isCancelable())
                .participants(participants.stream()
                        .map(participant -> toParticipantItem(participant, characterImageUrls))
                        .toList())
                .build();
    }

    private static ScheduleResDTO.Proposer toProposer(Member proposer) {
        if (proposer == null) {
            return null;
        }
        return ScheduleResDTO.Proposer.builder()
                .memberId(proposer.getId())
                .nickname(proposer.getDisplayName())
                .familyRole(proposer.getFamilyRole())
                .build();
    }

    private static Long resolveMissionId(Schedule schedule) {
        if (schedule.getBucketItem() == null || schedule.getBucketItem().getMission() == null) {
            return null;
        }
        return schedule.getBucketItem().getMission().getId();
    }

    private static ScheduleResDTO.ParticipantItem toParticipantItem(ScheduleParticipant participant,
                                                                    Map<FamilyRole, String> characterImageUrls) {
        Member member = participant.getMember();
        return ScheduleResDTO.ParticipantItem.builder()
                .nickname(member == null ? "탈퇴한 사용자" : member.getDisplayName())
                .status(toMyStatus(participant))
                .characterImageUrl(member == null ? null : characterImageUrls.get(member.getFamilyRole()))
                .build();
    }

    private static ScheduleDetailStatus toDetailStatus(Schedule schedule, LocalDate today) {
        return switch (schedule.getStatus()) {
            case PENDING_RESPONSE, NOT_CONFIRMED -> ScheduleDetailStatus.SCHEDULED;
            case CONFIRMED -> schedule.getScheduleDate().isEqual(today)
                    ? ScheduleDetailStatus.TODAY : ScheduleDetailStatus.CONFIRMED;
            case COMPLETED -> ScheduleDetailStatus.COMPLETED;
            case CANCELLED -> throw new ScheduleException(ScheduleErrorCode.SCHEDULE_CANCELLED);
        };
    }

    private static CalendarMyStatus toMyStatus(ScheduleParticipant participant) {
        if (participant == null) {
            return CalendarMyStatus.NOT_INVITED;
        }
        if (Boolean.TRUE.equals(participant.getReconfirmRequired())) {
            return CalendarMyStatus.RECONFIRM_REQUIRED;
        }
        return switch (participant.getStatus()) {
            case PENDING -> CalendarMyStatus.PENDING;
            case ACCEPTED -> CalendarMyStatus.ACCEPTED;
            case DECLINED -> CalendarMyStatus.DECLINED;
        };
    }
}
