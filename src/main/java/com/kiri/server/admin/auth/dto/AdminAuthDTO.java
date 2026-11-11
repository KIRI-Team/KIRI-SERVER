package com.kiri.server.admin.auth.dto;

import com.kiri.server.admin.auth.entity.Admin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class AdminAuthDTO {

    public record LoginRequest(
            @NotBlank(message = "아이디는 필수입니다.") String loginId,
            @NotBlank(message = "비밀번호는 필수입니다.") String password
    ) {
    }

    public record ReissueRequest(
            @NotBlank(message = "리프레시 토큰은 필수입니다.") String refreshToken
    ) {
    }

    public record TokenResponse(
            Long adminId,
            String name,
            String accessToken,
            String refreshToken,
            long expiresIn
    ) {
    }

    public record CreateAdminRequest(
            @NotBlank(message = "아이디는 필수입니다.")
            @Pattern(regexp = "^[a-zA-Z0-9._-]{4,50}$", message = "아이디는 영문·숫자·._- 4~50자입니다.")
            String loginId,
            @NotBlank(message = "비밀번호는 필수입니다.")
            @Size(min = 8, max = 72, message = "비밀번호는 8~72자입니다.")
            String password,
            @NotBlank(message = "이름은 필수입니다.")
            @Size(max = 50, message = "이름은 50자 이하입니다.")
            String name
    ) {
    }

    public record UpdateAdminRequest(
            @Size(max = 50, message = "이름은 50자 이하입니다.") String name,
            Boolean enabled,
            @Size(min = 8, max = 72, message = "비밀번호는 8~72자입니다.") String newPassword
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "현재 비밀번호는 필수입니다.") String currentPassword,
            @NotBlank(message = "새 비밀번호는 필수입니다.")
            @Size(min = 8, max = 72, message = "비밀번호는 8~72자입니다.")
            String newPassword
    ) {
    }

    public record AdminInfo(
            Long adminId,
            String loginId,
            String name,
            boolean enabled,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt
    ) {
        public static AdminInfo of(Admin admin) {
            return new AdminInfo(admin.getId(), admin.getLoginId(), admin.getName(), admin.isEnabled(),
                    admin.getLastLoginAt(), admin.getCreatedAt());
        }
    }
}
