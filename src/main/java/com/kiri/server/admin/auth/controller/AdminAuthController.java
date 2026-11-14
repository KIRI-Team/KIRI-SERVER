package com.kiri.server.admin.auth.controller;

import com.kiri.server.admin.auth.dto.AdminAuthDTO;
import com.kiri.server.admin.auth.service.AdminAccountService;
import com.kiri.server.admin.auth.service.AdminAuthService;
import com.kiri.server.admin.common.AdminSuccessCode;
import com.kiri.server.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "[관리자] 인증·계정")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final AdminAccountService adminAccountService;

    @Operation(summary = "로그인", description = "아이디·비밀번호로 로그인합니다. 실패 사유(아이디 없음/비밀번호 불일치/비활성)는 구분하지 않고 ADMIN401_1을 돌려줍니다.")
    @PostMapping("/auth/login")
    public ApiResponse<AdminAuthDTO.TokenResponse> login(@Valid @RequestBody AdminAuthDTO.LoginRequest request) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, adminAuthService.login(request));
    }

    @Operation(summary = "토큰 재발급", description = "리프레시 토큰으로 액세스·리프레시 토큰을 새로 발급합니다. 이전 리프레시 토큰은 무효가 됩니다.")
    @PostMapping("/auth/reissue")
    public ApiResponse<AdminAuthDTO.TokenResponse> reissue(@Valid @RequestBody AdminAuthDTO.ReissueRequest request) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, adminAuthService.reissue(request));
    }

    @Operation(summary = "로그아웃", description = "저장된 리프레시 토큰을 지웁니다.")
    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId) {
        adminAuthService.logout(adminId);
        return ApiResponse.onSuccess(AdminSuccessCode.OK);
    }

    @Operation(summary = "내 정보")
    @GetMapping("/me")
    public ApiResponse<AdminAuthDTO.AdminInfo> me(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, adminAccountService.me(adminId));
    }

    @Operation(summary = "내 비밀번호 변경", description = "변경하면 리프레시 토큰이 무효가 됩니다.")
    @PatchMapping("/me/password")
    public ApiResponse<Void> changeMyPassword(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                              @Valid @RequestBody AdminAuthDTO.ChangePasswordRequest request) {
        adminAccountService.changeMyPassword(adminId, request);
        return ApiResponse.onSuccess(AdminSuccessCode.OK);
    }

    @Operation(summary = "관리자 목록")
    @GetMapping("/admins")
    public ApiResponse<List<AdminAuthDTO.AdminInfo>> list() {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, adminAccountService.list());
    }

    @Operation(summary = "관리자 생성")
    @PostMapping("/admins")
    public ApiResponse<AdminAuthDTO.AdminInfo> create(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                      @Valid @RequestBody AdminAuthDTO.CreateAdminRequest request) {
        return ApiResponse.onSuccess(AdminSuccessCode.CREATED, adminAccountService.create(adminId, request));
    }

    @Operation(summary = "관리자 수정", description = "이름 변경, 활성/비활성, 비밀번호 재설정. 자기 자신은 비활성화할 수 없습니다. 비활성화하면 기존 토큰도 즉시 거부됩니다.")
    @PatchMapping("/admins/{targetAdminId}")
    public ApiResponse<AdminAuthDTO.AdminInfo> update(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                      @PathVariable Long targetAdminId,
                                                      @Valid @RequestBody AdminAuthDTO.UpdateAdminRequest request) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, adminAccountService.update(adminId, targetAdminId, request));
    }
}
