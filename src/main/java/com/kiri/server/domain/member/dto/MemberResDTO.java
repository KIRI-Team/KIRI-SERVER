package com.kiri.server.domain.member.dto;

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
}
