package com.kiri.server.domain.member.service;

import com.kiri.server.domain.member.converter.MemberConverter;
import com.kiri.server.domain.member.dto.MemberResDTO;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.enums.SocialProvider;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.oauth.OidcUserInfo;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.term.repository.MemberTermRepository;
import com.kiri.server.global.security.jwt.JwtProvider;
import com.kiri.server.global.util.HashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MemberWriter {

    private final MemberRepository memberRepository;
    private final MemberTermRepository memberTermRepository;
    private final JwtProvider jwtProvider;
    private final HashUtil hashUtil;
    private final MemberCreator memberCreator;

    @Transactional
    public MemberResDTO.Login persist(SocialProvider socialProvider, OidcUserInfo oidcUserInfo) {

        Member member = memberRepository.findBySocialProviderAndSocialProviderUid(socialProvider, oidcUserInfo.socialProviderUid()).orElse(null);
        boolean isNewUser = false;

        if (member == null) {
            try {
                member = memberCreator.create(socialProvider, oidcUserInfo);
                member = memberRepository.findByIdForUpdate(member.getId())
                        .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));
                isNewUser = true;
            } catch (DataIntegrityViolationException e) {
                member = memberRepository.findBySocialProviderAndSocialProviderUidForUpdate(socialProvider, oidcUserInfo.socialProviderUid()).orElseThrow(() -> e);
            }
        }

        String accessToken = jwtProvider.createAccessToken(member.getId());
        String refreshToken = jwtProvider.createRefreshToken(member.getId());
        member.updateRefreshTokenToHash(hashUtil.hash(refreshToken));

        boolean termsAgreed = memberTermRepository.areAllRequiredTermsAgreed(member.getId());

        return MemberConverter.toLoginResponse(accessToken, refreshToken, isNewUser, termsAgreed);
    }
}
