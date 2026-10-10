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
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "member",
        uniqueConstraints = {
                // 같은 소셜 계정으로 중복 가입 불가
                @UniqueConstraint(name = "uk_member_social", columnNames = {"social_provider", "social_provider_uid"}),
                // 닉네임은 서비스 전체에서 중복 불가
                @UniqueConstraint(name = "uk_member_nickname", columnNames = {"nickname"})
        }
)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    // 닉네임
    @Column(name = "nickname", length = 20)
    private String nickname;

    // 소셜 제공자
    @Enumerated(EnumType.STRING)
    @Column(name = "social_provider", length = 20)
    private SocialProvider socialProvider;

    // 소셜 UID (OAuth UID)
    @Column(name = "social_provider_uid")
    private String socialProviderUid;

    // 이메일
    @Column(name = "email")
    private String email;

    // 가족 내 역할
    @Enumerated(EnumType.STRING)
    @Column(name = "family_role", length = 20)
    private FamilyRole familyRole;

    // 리프레시 토큰 해시
    @Column(name = "refresh_token_hash", length = 64)
    private String refreshTokenHash;

    // 마지막 접속 일자
    @Column(name = "last_access_at")
    private LocalDateTime lastAccessAt;

    // 소속 가족 (가입 직후엔 가족이 없으므로 nullable)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private Family family;
}
