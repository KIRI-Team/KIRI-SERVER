package com.kiri.server.domain.member.controller;

import com.kiri.server.domain.member.controller.docs.MemberDeviceControllerDocs;
import com.kiri.server.domain.member.dto.MemberDeviceReqDTO;
import com.kiri.server.domain.member.exception.code.MemberDeviceSuccessCode;
import com.kiri.server.domain.member.service.MemberDeviceService;
import com.kiri.server.global.apiPayload.ApiResponse;
import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members/devices")
public class MemberDeviceController implements MemberDeviceControllerDocs {

    private final MemberDeviceService memberDeviceService;

    @Override
    @PostMapping
    public ApiResponse<Void> registerDevice(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody MemberDeviceReqDTO.Register dto
    ) {
        memberDeviceService.registerDevice(memberId, dto);
        BaseSuccessCode code = MemberDeviceSuccessCode.DEVICE_REGISTER_SUCCESS;
        return ApiResponse.onSuccess(code);
    }

    @Override
    @DeleteMapping
    public ApiResponse<Void> deleteDevice(
            @AuthenticationPrincipal Long memberId,
            @RequestParam String deviceIdentifier
    ) {
        memberDeviceService.deleteDevice(memberId, deviceIdentifier);
        BaseSuccessCode code = MemberDeviceSuccessCode.DEVICE_DELETE_SUCCESS;
        return ApiResponse.onSuccess(code);
    }
}
