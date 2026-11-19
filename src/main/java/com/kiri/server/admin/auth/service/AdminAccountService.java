package com.kiri.server.admin.auth.service;

import com.kiri.server.admin.audit.enums.AuditAction;
import com.kiri.server.admin.audit.enums.AuditTargetType;
import com.kiri.server.admin.audit.service.AdminAuditLogger;
import com.kiri.server.admin.auth.dto.AdminAuthDTO;
import com.kiri.server.admin.auth.entity.Admin;
import com.kiri.server.admin.auth.repository.AdminRepository;
import com.kiri.server.admin.common.AdminErrorCode;
import com.kiri.server.admin.common.AdminException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditLogger adminAuditLogger;

    @Transactional(readOnly = true)
    public List<AdminAuthDTO.AdminInfo> list() {
        return adminRepository.findAllByOrderByIdAsc().stream().map(AdminAuthDTO.AdminInfo::of).toList();
    }

    @Transactional(readOnly = true)
    public AdminAuthDTO.AdminInfo me(Long adminId) {
        return AdminAuthDTO.AdminInfo.of(get(adminId));
    }

    @Transactional
    public AdminAuthDTO.AdminInfo create(Long actorId, AdminAuthDTO.CreateAdminRequest request) {
        if (adminRepository.existsByLoginId(request.loginId())) {
            throw new AdminException(AdminErrorCode.DUPLICATE_LOGIN_ID);
        }
        Admin admin = adminRepository.save(Admin.builder()
                .loginId(request.loginId())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name().strip())
                .build());
        adminAuditLogger.log(actorId, AuditAction.CREATE, AuditTargetType.ADMIN, admin.getId(), null, null,
                Map.of("loginId", admin.getLoginId(), "name", admin.getName()));
        return AdminAuthDTO.AdminInfo.of(admin);
    }

    @Transactional
    public AdminAuthDTO.AdminInfo update(Long actorId, Long adminId, AdminAuthDTO.UpdateAdminRequest request) {
        Admin admin = get(adminId);
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();

        if (request.name() != null && !request.name().isBlank() && !request.name().strip().equals(admin.getName())) {
            before.put("name", admin.getName());
            admin.rename(request.name().strip());
            after.put("name", admin.getName());
        }
        if (request.enabled() != null && request.enabled() != admin.isEnabled()) {
            if (!request.enabled() && actorId.equals(adminId)) {
                throw new AdminException(AdminErrorCode.INVALID_REQUEST);
            }
            before.put("enabled", admin.isEnabled());
            if (request.enabled()) {
                admin.enable();
            } else {
                admin.disable();
            }
            after.put("enabled", admin.isEnabled());
        }
        if (request.newPassword() != null) {
            admin.changePassword(passwordEncoder.encode(request.newPassword()));
            after.put("password", "reset");
        }
        if (!after.isEmpty()) {
            adminAuditLogger.log(actorId, AuditAction.UPDATE, AuditTargetType.ADMIN, adminId, null, before, after);
        }
        return AdminAuthDTO.AdminInfo.of(admin);
    }

    @Transactional
    public void changeMyPassword(Long adminId, AdminAuthDTO.ChangePasswordRequest request) {
        Admin admin = get(adminId);
        if (!passwordEncoder.matches(request.currentPassword(), admin.getPasswordHash())) {
            throw new AdminException(AdminErrorCode.PASSWORD_MISMATCH);
        }
        admin.changePassword(passwordEncoder.encode(request.newPassword()));
        adminAuditLogger.log(adminId, AuditAction.UPDATE, AuditTargetType.ADMIN, adminId, null, null,
                Map.of("password", "changed"));
    }

    private Admin get(Long adminId) {
        return adminRepository.findById(adminId).orElseThrow(() -> new AdminException(AdminErrorCode.ADMIN_NOT_FOUND));
    }
}
