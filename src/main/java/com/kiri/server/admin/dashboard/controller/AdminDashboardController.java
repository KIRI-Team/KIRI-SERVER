package com.kiri.server.admin.dashboard.controller;

import com.kiri.server.admin.common.AdminSuccessCode;
import com.kiri.server.admin.dashboard.dto.DashboardDTO;
import com.kiri.server.admin.dashboard.service.AdminDashboardService;
import com.kiri.server.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "[관리자] 운영 대시보드")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @Operation(summary = "상단 KPI", description = """
            주(KST 월~일) 단위 KPI. 현재 값, 전주 값, 분자·분모·표본 수, 최근 8주 추이, 산식, 데이터 상태를 줍니다.
            - weekOf: 그 날짜가 속한 주. 생략하면 직전에 끝난 주
            - dataStatus: OK(정상, 0건 포함) / NOT_COLLECTED(미수집) / INSUFFICIENT_PERIOD(관찰 기간 미충족) / ERROR
            - 비율 KPI는 분모가 0이면 value가 null입니다.
            - 테스트 계정·가족은 제외합니다. 10분 캐시, fresh=true면 다시 계산합니다.
            - 목표값은 1차에서 제공하지 않습니다.""")
    @GetMapping("/kpis")
    public ApiResponse<List<DashboardDTO.Kpi>> kpis(
            @Parameter(description = "기준 날짜", example = "2026-11-03") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekOf,
            @RequestParam(defaultValue = "false") boolean fresh) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, dashboardService.kpis(weekOf, fresh));
    }

    @Operation(summary = "KPI 상세", description = "산식·분자·분모와 분자에 들어간 대상 목록(최대 100건). 완료 일정 KPI는 '리뷰 쓴 참여자 / 수락한 참여자' 분포를 함께 줍니다.")
    @GetMapping("/kpis/{key}")
    public ApiResponse<DashboardDTO.KpiDetail> detail(
            @PathVariable DashboardDTO.KpiKey key,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekOf) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, dashboardService.detail(key, weekOf));
    }

    @Operation(summary = "운영 주의 항목", description = "검수 대기·보류, 공개 대기, 수집 상태·재처리 대기, 미션 비공개로 노출 안 되는 끼리 픽, 리뷰가 있는데 완료되지 않은 일정")
    @GetMapping("/ops")
    public ApiResponse<DashboardDTO.Ops> ops() {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, dashboardService.ops());
    }
}
