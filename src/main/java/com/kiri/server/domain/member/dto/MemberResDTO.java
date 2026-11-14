package com.kiri.server.domain.member.dto;

import com.kiri.server.domain.member.enums.FamilyRole;
import lombok.Builder;

public class MemberResDTO {

    @Builder
    public record Login(
            String accessToken,
            String refreshToken,
            Boolean isNewUser,
            Boolean termsAgreed
    ) {}

    @Builder
    public record Nickname(
            String nickname
    ) {}

    @Builder
    public record TokenReissue(
            String accessToken,
            String refreshToken,
            Boolean termsAgreed
    ) {}

    @Builder
    public record SelectFamilyRole (
            FamilyRole familyRole
    ) {}

    @Builder
    public record MyInfo(
            Long memberId,
            String nickname,
            FamilyRole familyRole,
            String characterImageUrl,
            String familyName
    ) {}
}
