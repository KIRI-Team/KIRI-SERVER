package com.kiri.server.domain.family.entity;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "family",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_family_owner", columnNames = {"owner_id"}),
                @UniqueConstraint(name = "uk_family_name", columnNames = { "name" }),
                @UniqueConstraint(name = "uk_family_invite_code", columnNames = {"invite_code"}),
                @UniqueConstraint(name = "uk_family_invite_token", columnNames = { "invite_token" })
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Family extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "family_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false, unique = true)
    private Member owner;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "invite_code", length = 6)
    private String inviteCode;

    @Column(name = "invite_token", length = 32)
    private String inviteToken;

    @Column(name = "inviteExpiresAt")
    private LocalDateTime inviteExpiresAt;

    @Column(name = "region_code", length = 10)
    private String regionCode;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(name = "address")
    private String address;

    public void renewInvite(String inviteCode, String inviteToken, LocalDateTime inviteExpiresAt) {
        this.inviteCode = inviteCode;
        this.inviteToken = inviteToken;
        this.inviteExpiresAt = inviteExpiresAt;
    }
}
