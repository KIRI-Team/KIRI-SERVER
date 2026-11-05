package com.kiri.server.domain.schedule.controller;

import com.kiri.server.domain.schedule.controller.docs.ScheduleControllerDocs;
import com.kiri.server.domain.schedule.dto.ScheduleReqDTO;
import com.kiri.server.domain.schedule.dto.ScheduleResDTO;
import com.kiri.server.domain.schedule.exception.code.ScheduleSuccessCode;
import com.kiri.server.domain.schedule.service.ScheduleQueryService;
import com.kiri.server.global.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/schedules")
public class ScheduleController implements ScheduleControllerDocs {

    private final ScheduleQueryService scheduleQueryService;

    @GetMapping("/calendar")
    public ApiResponse<ScheduleResDTO.Calendar> getCalendar(
            @AuthenticationPrincipal Long memberId,
            @Valid @ModelAttribute ScheduleReqDTO.CalendarCondition condition
    ) {
        return ApiResponse.onSuccess(
                ScheduleSuccessCode.CALENDAR_SUCCESS,
                scheduleQueryService.getCalendar(memberId, condition)
        );
    }

    @GetMapping("/upcoming")
    public ApiResponse<ScheduleResDTO.UpcomingHome> getUpcoming(
            @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.onSuccess(
                ScheduleSuccessCode.UPCOMING_SUCCESS,
                scheduleQueryService.getUpcoming(memberId)
        );
    }

    @GetMapping("/{scheduleId}/conditions")
    public ApiResponse<ScheduleResDTO.Conditions> getConditions(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long scheduleId
    ) {
        return ApiResponse.onSuccess(
                ScheduleSuccessCode.CONDITION_SUCCESS,
                scheduleQueryService.getConditions(memberId, scheduleId)
        );
    }

    @GetMapping("/{scheduleId}/nearby")
    public ApiResponse<List<ScheduleResDTO.NearbyItem>> getNearby(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long scheduleId
    ) {
        return ApiResponse.onSuccess(
                ScheduleSuccessCode.NEARBY_SUCCESS,
                scheduleQueryService.getNearby(memberId, scheduleId)
        );
    }

    @GetMapping("/{scheduleId}")
    public ApiResponse<ScheduleResDTO.Detail> getDetail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long scheduleId
    ) {
        return ApiResponse.onSuccess(
                ScheduleSuccessCode.DETAIL_SUCCESS,
                scheduleQueryService.getDetail(memberId, scheduleId)
        );
    }
}
