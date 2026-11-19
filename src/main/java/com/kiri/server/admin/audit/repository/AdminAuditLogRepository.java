package com.kiri.server.admin.audit.repository;

import com.kiri.server.admin.audit.entity.AdminAuditLog;
import com.kiri.server.admin.audit.enums.AuditTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long>, JpaSpecificationExecutor<AdminAuditLog> {

    List<AdminAuditLog> findAllByTargetTypeAndTargetIdOrderByIdDesc(AuditTargetType targetType, Long targetId);
}
