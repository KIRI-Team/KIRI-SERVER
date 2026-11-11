package com.kiri.server.admin.audit.service;

import com.kiri.server.admin.audit.dto.AuditLogDTO;
import com.kiri.server.admin.audit.entity.AdminAuditLog;
import com.kiri.server.admin.audit.enums.AuditTargetType;
import com.kiri.server.admin.audit.repository.AdminAuditLogRepository;
import com.kiri.server.admin.auth.entity.Admin;
import com.kiri.server.admin.auth.repository.AdminRepository;
import com.kiri.server.admin.common.AdminPageRequest;
import com.kiri.server.admin.common.AdminPageResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminAuditQueryService {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final AdminRepository adminRepository;

    @Transactional(readOnly = true)
    public AdminPageResponse<AuditLogDTO.AuditLogItem> search(AuditLogDTO.Condition condition) {
        Specification<AdminAuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (condition.targetType() != null) {
                predicates.add(cb.equal(root.get("targetType"), condition.targetType()));
            }
            if (condition.targetId() != null) {
                predicates.add(cb.equal(root.get("targetId"), condition.targetId()));
            }
            if (condition.adminId() != null) {
                predicates.add(cb.equal(root.get("adminId"), condition.adminId()));
            }
            if (condition.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), condition.from().atStartOfDay()));
            }
            if (condition.to() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), condition.to().plusDays(1).atStartOfDay()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<AdminAuditLog> page = adminAuditLogRepository.findAll(spec,
                AdminPageRequest.of(condition.page(), condition.size(), Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, String> names = adminNames();
        return AdminPageResponse.of(page, log -> AuditLogDTO.AuditLogItem.of(log, names.get(log.getAdminId())));
    }

    @Transactional(readOnly = true)
    public List<AuditLogDTO.AuditLogItem> history(AuditTargetType targetType, Long targetId) {
        Map<Long, String> names = adminNames();
        return adminAuditLogRepository.findAllByTargetTypeAndTargetIdOrderByIdDesc(targetType, targetId).stream()
                .map(log -> AuditLogDTO.AuditLogItem.of(log, names.get(log.getAdminId())))
                .toList();
    }

    private Map<Long, String> adminNames() {
        return adminRepository.findAll().stream().collect(Collectors.toMap(Admin::getId, Admin::getName, (a, b) -> a));
    }
}
