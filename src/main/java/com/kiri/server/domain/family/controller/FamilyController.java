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
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/families")
public class FamilyController implements FamilyControllerDocs {

    private final FamilyService familyService;

    @Override
    @PostMapping
    public ApiResponse<FamilyResDTO.Create> createFamily(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody FamilyReqDTO.Create dto
    ) {
        BaseSuccessCode code = FamilySuccessCode.FAMILY_CREATED;
        return ApiResponse.onSuccess(code, familyService.createFamily(memberId, dto));
    }

    @Override
    @PatchMapping("/{familyId}/invite")
    public ApiResponse<FamilyResDTO.Invite> regenerateInvite(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long familyId
    ) {
        BaseSuccessCode code = FamilySuccessCode.INVITE_REGENERATED;
        return ApiResponse.onSuccess(code, familyService.regenerateInvite(memberId, familyId));
    }

    @Override
    @DeleteMapping("/{familyId}/invite")
    public ApiResponse<Void> deleteInvite(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long familyId
    ) {
        familyService.deleteInvite(memberId, familyId);
        BaseSuccessCode code = FamilySuccessCode.INVITE_DELETED;
        return ApiResponse.onSuccess(code);
    }

    @Override
    @GetMapping("/{familyId}/invite")
    public ApiResponse<FamilyResDTO.Invite> getInvite(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long familyId
    ) {
        BaseSuccessCode code = FamilySuccessCode.INVITE_FOUND;
        return ApiResponse.onSuccess(code, familyService.getInvite(memberId, familyId));
    }

    @Override
    @PatchMapping("/{familyId}")
    public ApiResponse<FamilyResDTO.UpdateName> updateFamilyName(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long familyId,
            @Valid @RequestBody FamilyReqDTO.UpdateName dto
    ) {
        BaseSuccessCode code = FamilySuccessCode.NAME_UPDATED;
        return ApiResponse.onSuccess(code, familyService.updateFamilyName(memberId, familyId, dto));
    }

    @Override
    @PutMapping("/{familyId}/address")
    public ApiResponse<FamilyResDTO.UpdateAddress> updateFamilyAddress(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long familyId,
            @Valid @RequestBody FamilyReqDTO.UpdateAddress dto
    ) {
        BaseSuccessCode code = FamilySuccessCode.ADDRESS_UPDATED;
        return ApiResponse.onSuccess(code, familyService.updateFamilyAddress(memberId, familyId, dto));
    }

    @Override
    @GetMapping("/me/id")
    public ApiResponse<FamilyResDTO.Id> getFamilyId(
            @AuthenticationPrincipal Long memberId
    ) {
        BaseSuccessCode code = FamilySuccessCode.FAMILY_ID_SUCCESS;
        return ApiResponse.onSuccess(code, familyService.getFamilyId(memberId));
    }

    @Override
    @PostMapping("/join/code")
    public ApiResponse<FamilyResDTO.Join> joinFamily(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody FamilyReqDTO.Join dto
    ) {
        BaseSuccessCode code = FamilySuccessCode.FAMILY_JOINED;
        return ApiResponse.onSuccess(code, familyService.joinFamily(memberId, dto));
    }

    @Override
    @GetMapping("/invite/{inviteToken}")
    public ApiResponse<FamilyResDTO.InviteTokenInfo> getInviteTokenInfo(
            @PathVariable String inviteToken
    ) {
        BaseSuccessCode code = FamilySuccessCode.FAMILY_INVITE_TOKEN_INFO_SUCCESS;
        return ApiResponse.onSuccess(code, familyService.getInviteTokenInfo(inviteToken));
    }

    @Override
    @PostMapping("/join/{inviteToken}")
    public ApiResponse<FamilyResDTO.Join> joinFamilyByToken(
            @AuthenticationPrincipal Long memberId,
            @PathVariable String inviteToken
    ) {
        BaseSuccessCode code = FamilySuccessCode.FAMILY_JOINED;
        return ApiResponse.onSuccess(code, familyService.joinFamilyByToken(memberId, inviteToken));
    }

    @Override
    @GetMapping("/{familyId}")
    public ApiResponse<FamilyResDTO.FamilyInfo> getFamilyInfo(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long familyId
    ) {
        BaseSuccessCode code = FamilySuccessCode.FAMILY_INFO_SUCCESS;
        return ApiResponse.onSuccess(code, familyService.getFamilyInfo(memberId, familyId));
    }
}
