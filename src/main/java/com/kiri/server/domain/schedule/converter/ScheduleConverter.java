package com.kiri.server.domain.schedule.converter;

import com.kiri.server.domain.schedule.dto.ScheduleResDTO;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleCondition;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.CalendarMyStatus;
import com.kiri.server.domain.schedule.enums.ConditionType;
import com.kiri.server.domain.schedule.enums.UpcomingCardType;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

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

    public static ScheduleResDTO.CalendarItem toCalendarItem(Schedule schedule, ScheduleParticipant myParticipant) {
        return ScheduleResDTO.CalendarItem.builder()
                .scheduleId(schedule.getId())
                .date(schedule.getScheduleDate())
                .time(schedule.getScheduleTime())
                .status(schedule.getStatus())
                .myStatus(toMyStatus(myParticipant))
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

    public static ScheduleResDTO.UpcomingHome toUpcomingHome(ScheduleResDTO.UpcomingCard responseRequired,
                                                             ScheduleResDTO.UpcomingCard upcoming) {
        return ScheduleResDTO.UpcomingHome.builder()
                .responseRequired(responseRequired)
                .upcoming(upcoming)
                .build();
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
