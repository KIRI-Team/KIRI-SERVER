package com.kiri.server.domain.family.entity;

import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "family")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Family extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "family_id")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "invite_code", length = 6)
    private String inviteCode;

    @Column(name = "invite_token", length = 64)
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
}
