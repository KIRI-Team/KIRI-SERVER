package com.kiri.server.admin.audit.dto;

import com.kiri.server.admin.audit.entity.AdminAuditLog;
import com.kiri.server.admin.audit.enums.AuditAction;
import com.kiri.server.admin.audit.enums.AuditTargetType;
import io.swagger.v3.oas.annotations.Parameter;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class AuditLogDTO {

    public record Condition(
            @Parameter(description = "대상 종류") AuditTargetType targetType,
            @Parameter(description = "대상 ID") Long targetId,
            @Parameter(description = "수행 관리자 ID") Long adminId,
            @Parameter(description = "시작일(포함)", example = "2026-11-01") LocalDate from,
            @Parameter(description = "종료일(포함)", example = "2026-11-30") LocalDate to,
            Integer page,
            Integer size
    ) {
    }

    public record AuditLogItem(
            Long auditLogId,
            Long adminId,
            String adminName,
            AuditAction action,
            AuditTargetType targetType,
            Long targetId,
            String reason,
            String before,
            String after,
            LocalDateTime createdAt
    ) {
        public static AuditLogItem of(AdminAuditLog log, String adminName) {
            return new AuditLogItem(log.getId(), log.getAdminId(), adminName, log.getAction(), log.getTargetType(),
                    log.getTargetId(), log.getReason(), log.getBeforeData(), log.getAfterData(), log.getCreatedAt());
        }
    }
}
