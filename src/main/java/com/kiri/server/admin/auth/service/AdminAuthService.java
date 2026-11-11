package com.kiri.server.admin.auth.service;

import com.kiri.server.admin.audit.enums.AuditAction;
import com.kiri.server.admin.audit.enums.AuditTargetType;
import com.kiri.server.admin.audit.service.AdminAuditLogger;
import com.kiri.server.admin.auth.dto.AdminAuthDTO;
import com.kiri.server.admin.auth.entity.Admin;
import com.kiri.server.admin.auth.repository.AdminRepository;
import com.kiri.server.admin.common.AdminErrorCode;
import com.kiri.server.admin.common.AdminException;
import com.kiri.server.global.security.jwt.AdminJwtProvider;
import com.kiri.server.global.util.HashUtil;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminJwtProvider adminJwtProvider;
    private final HashUtil hashUtil;
    private final AdminAuditLogger adminAuditLogger;
    private final Clock clock;

    // 아이디가 없을 때와 비밀번호가 틀렸을 때 같은 에러를 돌려준다.
    @Transactional
    public AdminAuthDTO.TokenResponse login(AdminAuthDTO.LoginRequest request) {
        Admin admin = adminRepository.findByLoginId(request.loginId())
                .filter(Admin::isEnabled)
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> new AdminException(AdminErrorCode.LOGIN_FAILED));

        String refreshToken = adminJwtProvider.createRefreshToken(admin.getId());
        admin.login(hashUtil.hash(refreshToken), LocalDateTime.now(clock));
        adminAuditLogger.log(admin.getId(), AuditAction.LOGIN, AuditTargetType.ADMIN, admin.getId(), null, null, null);
        return token(admin, refreshToken);
    }

    @Transactional
    public AdminAuthDTO.TokenResponse reissue(AdminAuthDTO.ReissueRequest request) {
        Long adminId;
        try {
            adminId = adminJwtProvider.getAdminIdFromRefreshToken(request.refreshToken());
        } catch (JwtException | IllegalArgumentException e) {
            throw new AdminException(AdminErrorCode.INVALID_REFRESH_TOKEN);
        }
        Admin admin = adminRepository.findById(adminId)
                .filter(Admin::isEnabled)
                .filter(found -> found.getRefreshTokenHash() != null
                        && hashUtil.matches(request.refreshToken(), found.getRefreshTokenHash()))
                .orElseThrow(() -> new AdminException(AdminErrorCode.INVALID_REFRESH_TOKEN));

        String refreshToken = adminJwtProvider.createRefreshToken(admin.getId());
        admin.rotateRefreshToken(hashUtil.hash(refreshToken));
        return token(admin, refreshToken);
    }

    @Transactional
    public void logout(Long adminId) {
        adminRepository.findById(adminId).ifPresent(Admin::logout);
    }

    private AdminAuthDTO.TokenResponse token(Admin admin, String refreshToken) {
        return new AdminAuthDTO.TokenResponse(admin.getId(), admin.getName(),
                adminJwtProvider.createAccessToken(admin.getId()), refreshToken,
                adminJwtProvider.accessTokenExpiresInSeconds());
    }
}
