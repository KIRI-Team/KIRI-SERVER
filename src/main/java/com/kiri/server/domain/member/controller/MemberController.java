package com.kiri.server.domain.member.controller;

import com.kiri.server.domain.member.controller.docs.MemberControllerDocs;
import com.kiri.server.domain.member.dto.MemberReqDTO;
import com.kiri.server.domain.member.dto.MemberResDTO;
import com.kiri.server.domain.member.service.MemberService;
import com.kiri.server.global.apiPayload.ApiResponse;
import com.kiri.server.global.apiPayload.code.AuthSuccessCode;
import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    @PostMapping("/v1/auth/reissue")
    public ApiResponse<MemberResDTO.TokenReissue> tokenReissue(
            @RequestBody @Valid MemberReqDTO.TokenReissue dto
    ) {
        BaseSuccessCode code = AuthSuccessCode.TOKEN_REISSUE_SUCCESS;
        return ApiResponse.onSuccess(code, memberService.tokenReissue(dto));
    }
}
