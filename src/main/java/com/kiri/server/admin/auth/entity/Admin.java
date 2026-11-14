package com.kiri.server.admin.auth.entity;

import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "admin")
public class Admin extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_id")
    private Long id;

    @Column(name = "login_id", length = 50, nullable = false, unique = true)
    private String loginId;

    @Column(name = "password_hash", length = 100, nullable = false)
    private String passwordHash;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "refresh_token_hash", length = 64)
    private String refreshTokenHash;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public void login(String refreshTokenHash, LocalDateTime now) {
        this.refreshTokenHash = refreshTokenHash;
        this.lastLoginAt = now;
    }

    public void rotateRefreshToken(String refreshTokenHash) {
        this.refreshTokenHash = refreshTokenHash;
    }

    public void logout() {
        this.refreshTokenHash = null;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.refreshTokenHash = null;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
        this.refreshTokenHash = null;
    }
}
