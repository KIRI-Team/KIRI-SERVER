package com.kiri.server.domain.member.controller;

import com.kiri.server.domain.member.controller.docs.MemberControllerDocs;
import com.kiri.server.domain.member.dto.MemberReqDTO;
import com.kiri.server.domain.member.dto.MemberResDTO;
import com.kiri.server.domain.member.exception.code.MemberSuccessCode;
import com.kiri.server.domain.member.service.MemberService;
import com.kiri.server.global.apiPayload.ApiResponse;
import com.kiri.server.global.apiPayload.code.AuthSuccessCode;
import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MemberController implements MemberControllerDocs {

    private final MemberService memberService;

    @Override
    @PostMapping("/v1/auth/login")
    public ApiResponse<MemberResDTO.Login> login(
            @RequestBody @Valid MemberReqDTO.Login dto
    ) {
        BaseSuccessCode code = AuthSuccessCode.LOGIN_SUCCESS;
        return ApiResponse.onSuccess(code, memberService.login(dto));
    }

    @Override
    @PatchMapping("/v1/members/nickname")
    public ApiResponse<MemberResDTO.Nickname> updateNickname(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody MemberReqDTO.Nickname dto
    ) {
        BaseSuccessCode code = MemberSuccessCode.NICKNAME_SET;
        return ApiResponse.onSuccess(code, memberService.updateNickname(memberId, dto));
    }

    @Override
    @PostMapping("/v1/auth/reissue")
    public ApiResponse<MemberResDTO.TokenReissue> tokenReissue(
            @RequestBody @Valid MemberReqDTO.TokenReissue dto
    ) {
        BaseSuccessCode code = AuthSuccessCode.TOKEN_REISSUE_SUCCESS;
        return ApiResponse.onSuccess(code, memberService.tokenReissue(dto));
    }

    @Override
    @PatchMapping("/v1/members/family-role")
    public ApiResponse<MemberResDTO.SelectFamilyRole> selectFamilyRole(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody MemberReqDTO.SelectFamilyRole dto
    ) {
        BaseSuccessCode code = MemberSuccessCode.FAMILY_ROLE_SET;
        return ApiResponse.onSuccess(code, memberService.selectFamilyRole(memberId, dto));
    }

    @Override
    @PostMapping("/v1/auth/logout")
    public ApiResponse<Void> logout(
            @AuthenticationPrincipal Long memberId
    ) {
        memberService.logout(memberId);
        BaseSuccessCode code = AuthSuccessCode.LOGOUT_SUCCESS;
        return ApiResponse.onSuccess(code);
    }

    @DeleteMapping("/v1/members/me")
    public ApiResponse<Void> withdraw(
            @AuthenticationPrincipal Long memberId
    ) {
        memberService.withdraw(memberId);
        BaseSuccessCode code = MemberSuccessCode.DELETE_SUCCESS;
        return ApiResponse.onSuccess(code);
    }
}
