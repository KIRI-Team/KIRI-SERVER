package com.kiri.server.domain.member.service;

import com.kiri.server.domain.member.dto.MemberReqDTO;
import com.kiri.server.domain.member.dto.MemberResDTO;
import com.kiri.server.domain.member.enums.SocialProvider;
import com.kiri.server.domain.member.oauth.AbstractOidcProvider;
import com.kiri.server.domain.member.oauth.OidcProviderFactory;
import com.kiri.server.domain.member.oauth.OidcUserInfo;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.global.apiPayload.code.AuthErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final OidcProviderFactory oidcProviderFactory;
    private final MemberWriter memberWriter;

    public MemberResDTO.Login login(MemberReqDTO.Login dto) {

        SocialProvider socialProvider;

        try {
            socialProvider = SocialProvider.valueOf(dto.socialProvider());
        } catch (IllegalArgumentException e) {
            throw new ProjectException(AuthErrorCode.UNSUPPORTED_PROVIDER, e);
        }

        AbstractOidcProvider oidcProvider = oidcProviderFactory.getSocialProvider(socialProvider);
        OidcUserInfo oidcUserInfo = oidcProvider.verify(dto.socialProviderToken());

        // DB에 회원 저장
        MemberResDTO.Login result = memberWriter.persist(socialProvider, oidcUserInfo);

        log.info("로그인 성공. provider={}, isNewUser={}", socialProvider, result.isNewUser());

        return result;
    }
}
