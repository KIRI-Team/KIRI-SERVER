package com.kiri.server.domain.schedule.converter;

import com.kiri.server.domain.schedule.dto.ScheduleResDTO;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.CalendarMyStatus;

import java.util.List;

public class ScheduleConverter {

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
