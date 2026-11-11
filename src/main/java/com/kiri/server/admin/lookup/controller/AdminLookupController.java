package com.kiri.server.admin.lookup.controller;

import com.kiri.server.admin.common.AdminPageResponse;
import com.kiri.server.admin.common.AdminSuccessCode;
import com.kiri.server.admin.lookup.dto.LookupDTO;
import com.kiri.server.admin.lookup.service.AdminLookupService;
import com.kiri.server.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "[관리자] 회원·가족·일정 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminLookupController {

    private final AdminLookupService adminLookupService;

    @Operation(summary = "회원 검색", description = "회원 ID, 닉네임·이메일, 가입 상태, 가족 연결 여부, 역할, 테스트 여부로 검색합니다. 탈퇴 회원은 닉네임·이메일이 지워져 회원 ID로만 찾을 수 있습니다.")
    @GetMapping("/members")
    public ApiResponse<AdminPageResponse<LookupDTO.MemberItem>> searchMembers(@ModelAttribute LookupDTO.MemberCondition condition) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, adminLookupService.searchMembers(condition));
    }

    @Operation(summary = "회원 상세", description = "가입·가족 연결 정보, 취향 설정 여부, 약관 동의, 활동 요약(버킷·일정·리뷰·답변 수)")
    @GetMapping("/members/{memberId}")
    public ApiResponse<LookupDTO.MemberDetail> getMember(@PathVariable Long memberId) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, adminLookupService.getMember(memberId));
    }

    @Operation(summary = "회원 테스트 계정 지정", description = "테스트 계정은 KPI와 집계에서 제외됩니다.")
    @PatchMapping("/members/{memberId}/test-flag")
    public ApiResponse<LookupDTO.MemberItem> markMemberTest(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                            @PathVariable Long memberId,
                                                            @Valid @RequestBody LookupDTO.TestFlagRequest request) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, adminLookupService.markMemberTest(adminId, memberId, request.isTest()));
    }

    @Operation(summary = "가족 검색")
    @GetMapping("/families")
    public ApiResponse<AdminPageResponse<LookupDTO.FamilyItem>> searchFamilies(@ModelAttribute LookupDTO.FamilyCondition condition) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, adminLookupService.searchFamilies(condition));
    }

    @Operation(summary = "가족 상세", description = "구성원·역할·가입 시각, 초대 상태, 버킷·일정 상태별 수, 리뷰 수. 상세 주소·좌표·답변·리뷰 본문은 민감정보 열람 API로만 제공합니다.")
    @GetMapping("/families/{familyId}")
    public ApiResponse<LookupDTO.FamilyDetail> getFamily(@PathVariable Long familyId) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, adminLookupService.getFamily(familyId));
    }

    @Operation(summary = "가족 테스트 지정", description = "테스트 가족은 KPI와 집계에서 제외됩니다.")
    @PatchMapping("/families/{familyId}/test-flag")
    public ApiResponse<LookupDTO.FamilyItem> markFamilyTest(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                            @PathVariable Long familyId,
                                                            @Valid @RequestBody LookupDTO.TestFlagRequest request) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, adminLookupService.markFamilyTest(adminId, familyId, request.isTest()));
    }

    @Operation(summary = "가족 민감정보 열람", description = "상세 주소·좌표, 최근 질문 답변 원문, 리뷰 본문·사진. 사유가 필수이며 열람 이력(VIEW_SENSITIVE)이 남습니다.")
    @PostMapping("/families/{familyId}/sensitive")
    public ApiResponse<LookupDTO.SensitiveFamily> viewSensitive(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                                @PathVariable Long familyId,
                                                                @RequestBody LookupDTO.SensitiveRequest request) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, adminLookupService.viewSensitive(adminId, familyId, request.reason()));
    }

    @Operation(summary = "가족 일정 목록")
    @GetMapping("/families/{familyId}/schedules")
    public ApiResponse<AdminPageResponse<LookupDTO.ScheduleItem>> searchSchedules(@PathVariable Long familyId,
                                                                                @ModelAttribute LookupDTO.ScheduleCondition condition) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, adminLookupService.searchFamilySchedules(familyId, condition));
    }

    @Operation(summary = "일정 상세", description = "참여자 응답, 연결 버킷, 리뷰 요약과 데이터 불일치 경고(REVIEW_WITHOUT_COMPLETION, COMPLETED_BUCKET_MISMATCH)")
    @GetMapping("/schedules/{scheduleId}")
    public ApiResponse<LookupDTO.ScheduleDetail> getSchedule(@PathVariable Long scheduleId) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, adminLookupService.getSchedule(scheduleId));
    }
}
