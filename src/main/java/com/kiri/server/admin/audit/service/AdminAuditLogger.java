package com.kiri.server.admin.audit.service;

import com.kiri.server.admin.audit.entity.AdminAuditLog;
import com.kiri.server.admin.audit.enums.AuditAction;
import com.kiri.server.admin.audit.enums.AuditTargetType;
import com.kiri.server.admin.audit.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

// 관리자 변경 이력. 호출한 서비스의 트랜잭션에 참여하므로 업무 변경이 롤백되면 이력도 함께 롤백된다.
@Component
@RequiredArgsConstructor
public class AdminAuditLogger {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void log(Long adminId, AuditAction action, AuditTargetType targetType, Long targetId,
                    String reason, Object before, Object after) {
        adminAuditLogRepository.save(AdminAuditLog.builder()
                .adminId(adminId)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .reason(reason)
                .beforeData(toJson(before))
                .afterData(toJson(after))
                .build());
    }

    private String toJson(Object value) {
        return value == null ? null : objectMapper.writeValueAsString(value);
    }
}
