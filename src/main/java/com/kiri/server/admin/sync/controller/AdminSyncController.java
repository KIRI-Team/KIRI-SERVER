package com.kiri.server.admin.sync.controller;

import com.kiri.server.admin.common.AdminSuccessCode;
import com.kiri.server.admin.sync.dto.SyncAdminDTO;
import com.kiri.server.admin.sync.service.AdminSyncService;
import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "[관리자] 외부 수집 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/sync")
public class AdminSyncController {

    private final AdminSyncService adminSyncService;

    @Operation(summary = "출처별 수집 현황", description = "마지막 상태·성공 시각, 진행 위치, 오늘 호출량과 한도, 실행 중 여부, 관리자 중단 여부, 원본 처리 상태별 건수(전체/최근 24시간)")
    @GetMapping("/sources")
    public ApiResponse<List<SyncAdminDTO.Source>> sources() {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, adminSyncService.sources());
    }

    @Operation(summary = "실패 재처리 목록", description = "exhausted=true는 최대 시도 횟수를 넘겨 자동 재처리에서 빠진 건입니다.")
    @GetMapping("/retries")
    public ApiResponse<List<SyncAdminDTO.Retry>> retries(@RequestParam(required = false) ExternalSource source) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, adminSyncService.retries(source));
    }

    @Operation(summary = "수동 실행", description = "INCREMENTAL은 증분 수집, FULL_CHECK는 누락 정리를 포함한 전체 점검입니다. 비동기로 시작합니다. 실행 중이면 409, 수집기가 없는 출처는 400, 관리자 중단 상태면 400입니다.")
    @PostMapping("/{source}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<SyncAdminDTO.RunAccepted> run(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                     @PathVariable ExternalSource source,
                                                     @RequestParam(required = false) SyncAdminDTO.RunMode mode) {
        return ApiResponse.onSuccess(AdminSuccessCode.ACCEPTED, adminSyncService.run(adminId, source, mode));
    }

    @Operation(summary = "수집 중단", description = "스케줄러와 수동 실행이 모두 건너뜁니다. 사유 필수.")
    @PostMapping("/{source}/pause")
    public ApiResponse<SyncAdminDTO.Source> pause(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                  @PathVariable ExternalSource source,
                                                  @RequestBody SyncAdminDTO.ReasonRequest request) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, adminSyncService.pause(adminId, source, request.reason()));
    }

    @Operation(summary = "수집 재개")
    @PostMapping("/{source}/resume")
    public ApiResponse<SyncAdminDTO.Source> resume(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                   @PathVariable ExternalSource source) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, adminSyncService.resume(adminId, source));
    }

    @Operation(summary = "실패 건 재처리 요청", description = "시도 횟수를 0으로 돌려 다음 수집 실행에서 다시 처리합니다. 원본 키가 유일해서 재처리로 중복 원본이나 미션이 생기지 않습니다.")
    @PostMapping("/retries/{retryId}/retry")
    public ApiResponse<Void> retry(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                   @PathVariable Long retryId) {
        adminSyncService.retry(adminId, retryId);
        return ApiResponse.onSuccess(AdminSuccessCode.OK);
    }

    @Operation(summary = "데이터 품질 점검", description = "좌표 이상, 기간 누락, 이미지 없음, 외부 목록에서 사라진 원본, 장기 미갱신 건수와 최근 20건")
    @GetMapping("/quality")
    public ApiResponse<List<SyncAdminDTO.QualityCheck>> quality() {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, adminSyncService.quality());
    }
}
