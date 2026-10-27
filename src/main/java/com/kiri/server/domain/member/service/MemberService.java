package com.kiri.server.domain.member.service;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.converter.MemberConverter;
import com.kiri.server.domain.member.dto.MemberReqDTO;
import com.kiri.server.domain.member.dto.MemberResDTO;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.enums.SocialProvider;
import com.kiri.server.domain.member.event.MemberWithdrawnEvent;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.oauth.AbstractOidcProvider;
import com.kiri.server.domain.member.oauth.OidcProviderFactory;
import com.kiri.server.domain.member.oauth.OidcUserInfo;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.term.repository.MemberTermRepository;
import com.kiri.server.global.apiPayload.code.AuthErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;
import com.kiri.server.global.security.jwt.JwtProvider;
import com.kiri.server.global.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberTermRepository memberTermRepository;
    private final OidcProviderFactory oidcProviderFactory;
    private final MemberWriter memberWriter;
    private final JwtProvider jwtProvider;
    private final HashUtil hashUtil;
    private final ApplicationEventPublisher applicationEventPublisher;

    public MemberResDTO.Login login(MemberReqDTO.Login dto) {

        SocialProvider socialProvider;

        try {
            socialProvider = SocialProvider.valueOf(dto.socialProvider());
        } catch (IllegalArgumentException e) {
            throw new ProjectException(AuthErrorCode.UNSUPPORTED_PROVIDER, e);
        }

        AbstractOidcProvider oidcProvider = oidcProviderFactory.getSocialProvider(socialProvider);
        OidcUserInfo oidcUserInfo = oidcProvider.verify(dto.socialProviderToken(), dto.nonce());

        // DB에 회원 저장
        MemberResDTO.Login result = memberWriter.persist(socialProvider, oidcUserInfo);

        log.info("로그인 성공. provider={}, isNewUser={}", socialProvider, result.isNewUser());

        return result;
    }

    @Transactional
    public MemberResDTO.Nickname updateNickname(Long memberId, MemberReqDTO.Nickname dto) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));

        String nickname = dto.nickname();

        if (memberRepository.existsByNickname(nickname)) {
            throw new ProjectException(MemberErrorCode.DUPLICATE_NICKNAME);
        }

        member.updateNickname(nickname);

        return MemberConverter.toNicknameResponse(nickname);
    }

    public MemberResDTO.TokenReissue tokenReissue(MemberReqDTO.TokenReissue dto) {
        String refreshToken = dto.refreshToken();

        if (!jwtProvider.isValid(refreshToken) || !jwtProvider.isRefreshToken(refreshToken)) {
            throw new ProjectException(AuthErrorCode.INVALID_TOKEN);
        }

        Long memberId = jwtProvider.getMemberIdFromRefreshToken(refreshToken);
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new MemberException(AuthErrorCode.INVALID_TOKEN));

        if (!hashUtil.matches(refreshToken, member.getRefreshTokenHash())) {
            throw new ProjectException(AuthErrorCode.INVALID_TOKEN);
        }

        String newAccessToken = jwtProvider.createAccessToken(memberId);
        String newRefreshToken = jwtProvider.createRefreshToken(memberId);
        member.updateRefreshTokenToHash(hashUtil.hash(newRefreshToken));

        boolean termsAgreed = memberTermRepository.areAllRequiredTermsAgreed(member.getId());

        return MemberConverter.toTokenReissueResponse(newAccessToken, newRefreshToken, termsAgreed);
    }

    @Transactional
    public MemberResDTO.SelectFamilyRole selectFamilyRole(Long memberId, MemberReqDTO.SelectFamilyRole dto) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));

        member.updateFamilyRole(dto.familyRole());

        return MemberConverter.toFamilyRoleResponse(dto.familyRole());
    }

    @Transactional
    public void logout(Long memberId) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));
        member.deleteRefreshToken();
    }

    @Transactional
    public void withdraw(Long memberId) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));

        if (member.isWithdrawn()) {
            throw new ProjectException(MemberErrorCode.ALREADY_WITHDRAWN);
        }

        Family family = member.getFamily();

        if (family != null && family.isOwner(member)) {

            Member nextOwner = memberRepository.findFirstByFamilyAndDeletedAtIsNullAndIdNotOrderByFamilyJoinedAtAsc(family, memberId).orElse(null);

            if (nextOwner != null) {
                // 다른 활성 구성원이 있으면 owner 승계
                family.changeOwner(nextOwner);
            } else {
                // 마지막 구성원이면 가족 공간 종료
                // family.delete();
            }
        }

        applicationEventPublisher.publishEvent(new MemberWithdrawnEvent(memberId));
        member.withdraw();
    }
}
