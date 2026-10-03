package com.kiri.server.domain.member.service;

import com.kiri.server.domain.member.dto.MemberResDTO;
import com.kiri.server.domain.member.enums.SocialProvider;
import com.kiri.server.domain.member.oauth.OidcUserInfo;
import com.kiri.server.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberWriter {

    private final MemberRepository memberRepository;

    public MemberResDTO.Login persist(SocialProvider socialProvider, OidcUserInfo oidcUserInfo) {
        return null;
    }
}
