package com.kiri.server.domain.schedule.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.kiri.server.domain.schedule.enums.CalendarMyStatus;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public class ScheduleResDTO {

    @Builder
    public record Calendar(
            int year,
            int month,
            List<CalendarItem> schedules
    ) {
    }

    @Builder
    public record CalendarItem(
            Long scheduleId,
            @JsonFormat(pattern = "yyyy-MM-dd")
            LocalDate date,
            @JsonFormat(pattern = "HH:mm")
            LocalTime time,
            ScheduleStatus status,
            CalendarMyStatus myStatus
    ) {
    }

    @Builder
    public record Created(
            Long scheduleId,
            Long bucketItemId,
            @JsonFormat(pattern = "yyyy-MM-dd")
            LocalDate scheduleDate,
            @JsonFormat(pattern = "HH:mm")
            LocalTime scheduleTime,
            ScheduleStatus status,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
            LocalDateTime responseDeadline,
            Long proposerId,
            List<Long> participantMemberIds
    ) {
    }
}
