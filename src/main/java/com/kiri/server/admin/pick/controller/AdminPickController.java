package com.kiri.server.admin.pick.controller;

import com.kiri.server.admin.common.AdminSuccessCode;
import com.kiri.server.admin.pick.dto.PickDTO;
import com.kiri.server.admin.pick.service.AdminPickService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "[관리자] 끼리 픽")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/kiri-picks")
public class AdminPickController {

    private final AdminPickService pickService;

    @Operation(summary = "끼리 픽 목록", description = "노출 순서대로. exposure가 EXPOSED인 것만 앱 미션 목록 첫 페이지 상단에 실제로 보입니다. MISSION_NOT_ACTIVE는 편성은 켜져 있지만 미션이 비공개라 노출되지 않는 상태입니다.")
    @GetMapping
    public ApiResponse<List<PickDTO.PickItem>> list() {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, pickService.list());
    }

    @Operation(summary = "끼리 픽 상세")
    @GetMapping("/{kiriPickId}")
    public ApiResponse<PickDTO.PickItem> get(@PathVariable Long kiriPickId) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, pickService.get(kiriPickId));
    }

    @Operation(summary = "끼리 픽 편성", description = "공개(ACTIVE) 미션만 편성할 수 있습니다. 장소에 미션이 여러 개면 대표 미션을 고르세요.")
    @PostMapping
    public ApiResponse<PickDTO.PickItem> create(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                @Valid @RequestBody PickDTO.PickRequest request) {
        return ApiResponse.onSuccess(AdminSuccessCode.CREATED, pickService.create(adminId, request));
    }

    @Operation(summary = "끼리 픽 수정", description = "active=false로 노출을 끕니다.")
    @PatchMapping("/{kiriPickId}")
    public ApiResponse<PickDTO.PickItem> update(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                @PathVariable Long kiriPickId,
                                                @Valid @RequestBody PickDTO.PickRequest request) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, pickService.update(adminId, kiriPickId, request));
    }

    @Operation(summary = "노출 순서 변경", description = "전달한 순서대로 0부터 다시 매깁니다.")
    @PutMapping("/order")
    public ApiResponse<List<PickDTO.PickItem>> reorder(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                       @Valid @RequestBody PickDTO.OrderRequest request) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, pickService.reorder(adminId, request));
    }

    @Operation(summary = "끼리 픽 성과", description = "저장·일정·완료·별점·재참여 의향. 버킷 출처로 한 픽에만 귀속합니다. 노출·클릭은 앱 계측 전까지 null(미수집)입니다. 실제 방문·매출이 아닙니다.")
    @GetMapping("/{kiriPickId}/stats")
    public ApiResponse<PickDTO.PickStats> stats(@PathVariable Long kiriPickId) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, pickService.stats(kiriPickId));
    }
}
