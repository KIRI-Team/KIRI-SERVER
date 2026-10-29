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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
