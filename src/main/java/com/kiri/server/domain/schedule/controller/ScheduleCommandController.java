package com.kiri.server.domain.schedule.controller;

import com.kiri.server.domain.schedule.controller.docs.ScheduleCommandControllerDocs;
import com.kiri.server.domain.schedule.dto.ScheduleReqDTO;
import com.kiri.server.domain.schedule.dto.ScheduleResDTO;
import com.kiri.server.domain.schedule.exception.code.ScheduleSuccessCode;
import com.kiri.server.domain.schedule.service.ScheduleCommandService;
import com.kiri.server.domain.schedule.service.ScheduleResponseService;
import com.kiri.server.global.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ScheduleCommandController implements ScheduleCommandControllerDocs {

    private final ScheduleCommandService scheduleCommandService;
    private final ScheduleResponseService scheduleResponseService;

    @PostMapping("/bucket-items/{bucketItemId}/schedules")
    public ApiResponse<ScheduleResDTO.Created> createSchedule(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long bucketItemId,
            @Valid @RequestBody ScheduleReqDTO.Create request
    ) {
        return ApiResponse.onSuccess(
                ScheduleSuccessCode.CREATE_SUCCESS,
                scheduleCommandService.create(memberId, bucketItemId, request)
        );
    }

    @PatchMapping("/schedules/{scheduleId}/response")
    public ApiResponse<ScheduleResDTO.Responded> respondSchedule(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long scheduleId,
            @Valid @RequestBody ScheduleReqDTO.Response request
    ) {
        return ApiResponse.onSuccess(
                ScheduleSuccessCode.RESPONSE_SUCCESS,
                scheduleResponseService.respond(memberId, scheduleId, request)
        );
    }
}
