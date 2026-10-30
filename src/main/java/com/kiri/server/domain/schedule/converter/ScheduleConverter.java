package com.kiri.server.domain.schedule.converter;

import com.kiri.server.domain.schedule.dto.ScheduleResDTO;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.CalendarMyStatus;

import java.util.List;

public class ScheduleConverter {

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
