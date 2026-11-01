package com.kiri.server.domain.schedule.converter;

import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.place.entity.Place;
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

    public static ScheduleResDTO.Conditions toConditions(Schedule schedule, List<ConditionType> conditions) {
        Mission mission = schedule.getBucketItem().getMission();
        return ScheduleResDTO.Conditions.builder()
                .missionTitle(mission.getTitle())
                .estimatedStepsMin(mission.getEstimatedStepsMin())
                .estimatedStepsMax(mission.getEstimatedStepsMax())
                .durationMinMinutes(mission.getDurationMinMinutes())
                .durationMaxMinutes(mission.getDurationMaxMinutes())
                .conditions(conditions)
                .build();
    }

    public static ScheduleResDTO.NearbyItem toNearbyItem(Mission mission) {
        Place place = mission.getPlace();
        return ScheduleResDTO.NearbyItem.builder()
                .placeName(place.getName())
                .region(toRegion(place.getAddress()))
                .description(mission.getRecommendationReason())
                .imageUrl(place.getImageUrl() != null ? place.getImageUrl() : mission.getImageUrl())
                .build();
    }

    public static ScheduleResDTO.UpcomingHome toUpcomingHome(ScheduleResDTO.UpcomingCard responseRequired,
                                                             ScheduleResDTO.UpcomingCard upcoming) {
        return ScheduleResDTO.UpcomingHome.builder()
                .responseRequired(responseRequired)
                .upcoming(upcoming)
                .build();
    }

    private static String toRegion(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String[] tokens = address.trim().split("\\s+");
        String province = tokens[0]
                .replaceFirst("(특별자치시|특별시|광역시)$", "시")
                .replaceFirst("특별자치도$", "도");
        if (tokens.length < 2 || !tokens[1].matches(".+[시군구]$")) {
            return province;
        }
        return province + " " + tokens[1];
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
