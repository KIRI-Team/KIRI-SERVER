package com.kiri.server.admin;

import com.kiri.server.admin.auth.entity.Admin;
import com.kiri.server.admin.auth.repository.AdminRepository;
import com.kiri.server.global.security.jwt.AdminJwtProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// 관리자 통합 테스트 공통: 관리자 계정을 만들고 토큰을 발급한다.
@Component
public class AdminTestSupport {

    public static final String PASSWORD = "password123!";

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminJwtProvider adminJwtProvider;

    public AdminTestSupport(AdminRepository adminRepository, PasswordEncoder passwordEncoder,
                            AdminJwtProvider adminJwtProvider) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminJwtProvider = adminJwtProvider;
    }

    public Admin createAdmin(String loginId) {
        return adminRepository.save(Admin.builder()
                .loginId(loginId)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .name(loginId + "-name")
                .build());
    }

    public String bearer(Admin admin) {
        return "Bearer " + adminJwtProvider.createAccessToken(admin.getId());
    }
}
