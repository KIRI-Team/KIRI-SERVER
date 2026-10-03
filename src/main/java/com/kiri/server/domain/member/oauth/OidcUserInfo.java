package com.kiri.server.domain.member.oauth;

public record OidcUserInfo(
        String providerId,
        String email
) {}