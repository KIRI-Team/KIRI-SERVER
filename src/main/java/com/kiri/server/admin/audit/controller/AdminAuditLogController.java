package com.kiri.server.admin.audit.controller;

import com.kiri.server.admin.audit.dto.AuditLogDTO;
import com.kiri.server.admin.audit.service.AdminAuditQueryService;
import com.kiri.server.admin.common.AdminPageResponse;
import com.kiri.server.admin.common.AdminSuccessCode;
import com.kiri.server.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "[관리자] 감사 로그")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditLogController {

    private final AdminAuditQueryService adminAuditQueryService;

    @Operation(summary = "감사 로그 검색", description = "대상 종류·ID·관리자·기간으로 관리자 변경 이력을 최신순으로 조회합니다.")
    @GetMapping
    public ApiResponse<AdminPageResponse<AuditLogDTO.AuditLogItem>> search(@ParameterObject @ModelAttribute AuditLogDTO.Condition condition) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, adminAuditQueryService.search(condition));
    }
}
