package com.kiri.server.domain.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.kiri.server.domain.mission.enums.WalkingLevel;
import com.kiri.server.domain.schedule.enums.CalendarMyStatus;
import com.kiri.server.domain.schedule.enums.ConditionType;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import com.kiri.server.domain.schedule.enums.ScheduleDetailStatus;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.domain.schedule.enums.UpcomingCardType;
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
            CalendarMyStatus myStatus,
            String reviewImageUrl
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

    @Builder
    public record Responded(
            Long scheduleId,
            ParticipantStatus myStatus,
            ScheduleStatus status
    ) {
    }

    @Builder
    public record Cancelled(
            Long scheduleId,
            ScheduleStatus status,
            Long bucketItemId
    ) {
    }

    @Builder
    public record Updated(
            Long scheduleId,
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

    @Builder
    public record UpcomingHome(
            UpcomingCard responseRequired,
            UpcomingCard upcoming
    ) {
    }

    @Builder
    public record UpcomingCard(
            Long scheduleId,
            @JsonFormat(pattern = "yyyy-MM-dd")
            LocalDate date,
            @JsonFormat(pattern = "HH:mm")
            LocalTime time,
            ScheduleStatus status,
            CalendarMyStatus myStatus,
            Integer dDay,
            UpcomingCardType type,
            String title
    ) {
    }

    @Builder
    public record Conditions(
            String missionTitle,
            WalkingLevel walkingLevel,
            Integer durationMinMinutes,
            Integer durationMaxMinutes,
            List<ConditionType> conditions
    ) {
    }

    @Builder
    public record NearbyItem(
            String placeName,
            String region,
            String description,
            String imageUrl,
            Integer price
    ) {
    }

    @Builder
    @Schema(name = "ScheduleDetailResponse", description = "일정 상세 조회 응답")
    public record Detail(
            Long missionId,
            @JsonFormat(pattern = "yyyy-MM-dd")
            LocalDate date,
            @JsonFormat(pattern = "HH:mm")
            LocalTime time,
            ScheduleDetailStatus status,
            List<ParticipantItem> participants
    ) {
    }

    @Builder
    public record ParticipantItem(
            String nickname,
            boolean accepted
    ) {
    }
}
