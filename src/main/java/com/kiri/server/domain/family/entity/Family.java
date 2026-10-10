package com.kiri.server.domain.family.entity;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "family",
        uniqueConstraints = {
                // 가족명은 서비스 전체에서 중복 불가
                @UniqueConstraint(name = "uk_family_name", columnNames = {"name"}),
                // 한 회원은 가족을 하나만 생성 가능
                @UniqueConstraint(name = "uk_family_owner", columnNames = {"owner_id"}),
                // 초대 코드로 가족을 찾으므로 가족 간 중복 불가
                @UniqueConstraint(name = "uk_family_invite_code", columnNames = {"invite_code"}),
                // 초대 토큰으로 가족을 찾으므로 가족 간 중복 불가
                @UniqueConstraint(name = "uk_family_invite_token", columnNames = {"invite_token"})
        }
)
public class Family extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "family_id")
    private Long id;

    // 가족명
    @Column(name = "name", length = 20, nullable = false)
    private String name;

    // 초대 코드
    @Column(name = "invite_code", length = 6)
    private String inviteCode;

    // 초대 토큰
    @Column(name = "invite_token", length = 64)
    private String inviteToken;

    // 초대 만료일시
    @Column(name = "invite_expires_at")
    private LocalDateTime inviteExpiresAt;

    // 지역 코드
    @Column(name = "region_code", length = 10)
    private String regionCode;

    // 위도
    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    // 경도
    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    // 주소
    @Column(name = "address", length = 255)
    private String address;

    // 가족을 만든 회원 (방장)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private Member owner;
}
