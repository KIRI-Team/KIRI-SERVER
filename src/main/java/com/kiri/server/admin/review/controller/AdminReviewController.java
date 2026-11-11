package com.kiri.server.admin.review.controller;

import com.kiri.server.admin.common.AdminPageResponse;
import com.kiri.server.admin.common.AdminSuccessCode;
import com.kiri.server.admin.content.dto.MissionFormDTO;
import com.kiri.server.admin.review.dto.ReviewDTO;
import com.kiri.server.admin.review.service.AdminReviewCommandService;
import com.kiri.server.admin.review.service.AdminReviewQueryService;
import com.kiri.server.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.List;

@Tag(name = "[관리자] 미션 검수")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/reviews")
public class AdminReviewController {

    private final AdminReviewQueryService queryService;
    private final AdminReviewCommandService commandService;

    // ===== 탭 ① 검증 실패 =====

    @Operation(summary = "[탭①] 검증 실패 대기열", description = "외부 원본 중 자동 검증을 통과하지 못한(REVIEW_REQUIRED) 건. 오래 기다린 순. previouslyApproved=true는 이전에 승인했는데 원본이 바뀌어 다시 들어온 건입니다.")
    @GetMapping
    public ApiResponse<AdminPageResponse<ReviewDTO.QueueItem>> queue(@ModelAttribute ReviewDTO.QueueCondition condition) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, queryService.queue(condition));
    }

    @Operation(summary = "[탭①] 검수 상세", description = "외부 원본·직전 원본과 바뀐 필드, 자동 생성 결과, 입력 초기값(suggestion), 기존 미션, 중복 후보, 이전 승인 때 보완 값(previousOverrides), 작업·변경 이력")
    @GetMapping("/{externalRecordId}")
    public ApiResponse<ReviewDTO.Detail> detail(@PathVariable Long externalRecordId) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, queryService.detail(externalRecordId));
    }

    @Operation(summary = "[탭①] 담당자 지정", description = "assigneeId를 비우면 담당자를 해제합니다.")
    @PatchMapping("/{externalRecordId}/assignee")
    public ApiResponse<ReviewDTO.Task> assign(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                              @PathVariable Long externalRecordId,
                                              @RequestBody ReviewDTO.AssignRequest request) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, commandService.assign(adminId, externalRecordId, request.assigneeId()));
    }

    @Operation(summary = "[탭①] 재검증", description = "보완한 값을 저장하지 않고 공개 조건만 검사합니다.")
    @PostMapping("/{externalRecordId}/revalidate")
    public ApiResponse<MissionFormDTO.ValidationResult> revalidate(@PathVariable Long externalRecordId,
                                                                   @RequestBody MissionFormDTO.MissionForm form) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, commandService.revalidate(form));
    }

    @Operation(summary = "[탭①] 승인", description = """
            보완한 값으로 미션을 만들거나 고치고 공개합니다. 검증에 실패하면 ADMIN400_3과 필드별 오류를 돌려줍니다.
            - 운영자가 바꾼 필드는 잠겨서 외부 재수집이 덮어쓰지 않습니다.
            - 기존 미션이 종료·취소·숨김 상태면 상태를 바꾸지 않습니다.
            - 이미 처리된 건이면 ADMIN409_5입니다.""")
    @PostMapping("/{externalRecordId}/approve")
    public ApiResponse<ReviewDTO.ApproveResult> approve(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                        @PathVariable Long externalRecordId,
                                                        @RequestBody ReviewDTO.ApproveRequest request) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, commandService.approve(adminId, externalRecordId, request));
    }

    @Operation(summary = "[탭①] 보류", description = "업무 상태만 ON_HOLD로 바꿉니다. 사유 필수.")
    @PostMapping("/{externalRecordId}/hold")
    public ApiResponse<ReviewDTO.Task> hold(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                            @PathVariable Long externalRecordId,
                                            @RequestBody ReviewDTO.ReasonRequest request) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, commandService.hold(adminId, externalRecordId, request.reason()));
    }

    @Operation(summary = "[탭①] 제외", description = "원본을 ADMIN_EXCLUDED로 제외합니다. 연결된 공개 미션은 숨깁니다. 재수집해도 다시 살아나지 않습니다. 사유 필수.")
    @PostMapping("/{externalRecordId}/reject")
    public ApiResponse<ReviewDTO.Task> reject(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                              @PathVariable Long externalRecordId,
                                              @RequestBody ReviewDTO.ReasonRequest request) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, commandService.reject(adminId, externalRecordId, request.reason()));
    }

    // ===== 탭 ② 공개 대기 =====

    @Operation(summary = "[탭②] 공개 대기 목록", description = "자동 검증은 통과했지만 자동 공개가 꺼져 있어 CHECK_REQUIRED로 남은 미션")
    @GetMapping("/pending-publish")
    public ApiResponse<AdminPageResponse<ReviewDTO.PendingItem>> pending(@ModelAttribute ReviewDTO.PendingCondition condition) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, queryService.pendingPublish(condition));
    }

    @Operation(summary = "[탭②] 노출 켜기", description = "ACTIVE로 공개합니다. 기간이 지났으면 바로 ENDED가 됩니다.")
    @PostMapping("/pending-publish/{missionId}/publish")
    public ApiResponse<ReviewDTO.PublishResult> publish(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                        @PathVariable Long missionId) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, commandService.publish(adminId, missionId));
    }

    @Operation(summary = "[탭②] 노출 끄기", description = "HIDDEN으로 숨깁니다. 재수집해도 다시 노출되지 않습니다. 사유 필수.")
    @PostMapping("/pending-publish/{missionId}/hide")
    public ApiResponse<ReviewDTO.PublishResult> hide(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                     @PathVariable Long missionId,
                                                     @RequestBody ReviewDTO.ReasonRequest request) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, commandService.hidePending(adminId, missionId, request.reason()));
    }

    @Operation(summary = "[탭②] 일괄 처리", description = "최대 100건. 하나라도 공개 대기 상태가 아니면 전체를 취소합니다. HIDE는 사유 필수.")
    @PostMapping("/pending-publish/bulk")
    public ApiResponse<List<ReviewDTO.PublishResult>> bulk(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                           @RequestBody ReviewDTO.BulkRequest request) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, commandService.bulk(adminId, request));
    }
}
