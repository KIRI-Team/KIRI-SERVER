package com.kiri.server.domain.member.entity;

import com.kiri.server.domain.member.enums.FamilyRole;
import com.kiri.server.domain.member.enums.SocialProvider;
import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name ="member",
        uniqueConstraints ={
                @UniqueConstraint(name = "uk_member_social_provider", columnNames = { "social_provider", "social_provider_uid"})
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

    @Column(name = "nickname")
    private String nickname;

    @Enumerated(EnumType.STRING)
    private SocialProvider socialProvider;

    @Column(name = "social_provider_uid")
    private String socialProviderUid;

    @Column(name = "email")
    private String email;

    @Column(name = "refreshTokenHash", length = 64)
    private String refreshTokenHash;

    @Enumerated(EnumType.STRING)
    private FamilyRole familyRole;

    @Column(name = "last_access_at")
    private LocalDateTime lastAccessAt;
}
