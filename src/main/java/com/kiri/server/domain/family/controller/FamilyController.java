package com.kiri.server.domain.family.controller;

import com.kiri.server.domain.family.controller.docs.FamilyControllerDocs;
import com.kiri.server.domain.family.dto.FamilyReqDTO;
import com.kiri.server.domain.family.dto.FamilyResDTO;
import com.kiri.server.domain.family.exception.code.FamilySuccessCode;
import com.kiri.server.domain.family.service.FamilyService;
import com.kiri.server.global.apiPayload.ApiResponse;
import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/families")
public class FamilyController implements FamilyControllerDocs {

    private final FamilyService familyService;

    @PostMapping
    public ApiResponse<FamilyResDTO.Create> createFamily(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody FamilyReqDTO.Create dto
    ) {
        BaseSuccessCode code = FamilySuccessCode.FAMILY_CREATED;
        return ApiResponse.onSuccess(code, familyService.createFamily(memberId, dto));
    }
}
