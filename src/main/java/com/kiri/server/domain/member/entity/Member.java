package com.kiri.server.domain.member.entity;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.enums.FamilyRole;
import com.kiri.server.domain.member.enums.SocialProvider;
import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name ="member",
        uniqueConstraints ={
                @UniqueConstraint(name = "uk_member_social", columnNames = {"social_provider", "social_provider_uid"}),
                @UniqueConstraint(name = "uk_member_nickname", columnNames = {"nickname"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private Family family;

    @Column(name = "nickname", unique = true, length=20)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_provider", length = 20)
    private SocialProvider socialProvider;

    @Column(name = "social_provider_uid")
    private String socialProviderUid;

    @Column(name = "email")
    private String email;

    @Column(name = "refresh_token_hash", length = 64)
    private String refreshTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "family_role", length = 20)
    private FamilyRole familyRole;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "family_joined_at")
    private LocalDateTime familyJoinedAt;

    public void updateRefreshTokenToHash(String refreshTokenHash) {
        this.refreshTokenHash = refreshTokenHash;
    }

    public void assignFamily(Family family) {
        this.family = family;
    }

    public void joinFamily(Family family) {
        this.familyJoinedAt = LocalDateTime.now();
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updateFamilyRole(FamilyRole familyRole) {
        this.familyRole = familyRole;
    }

    public void deleteRefreshToken() {
        this.refreshTokenHash = null;
    }

    public void withdraw() {
        this.deletedAt = LocalDateTime.now();
        this.nickname = null;
        this.email = null;
        this.socialProvider = null;
        this.socialProviderUid = null;
        this.refreshTokenHash = null;
        this.familyRole = null;
    }

    public boolean isWithdrawn() {
        return deletedAt != null;
    }

    public String getDisplayName() {
        return isWithdrawn() ? "탈퇴한 사용자" : nickname;
    }
}
