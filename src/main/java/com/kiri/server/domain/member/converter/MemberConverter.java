package com.kiri.server.domain.member.converter;

import com.kiri.server.domain.member.dto.MemberResDTO;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.enums.SocialProvider;
import com.kiri.server.domain.member.oauth.OidcUserInfo;

public class MemberConverter {

    public static Member toMember(SocialProvider socialProvider, OidcUserInfo oidcUserInfo) {
        return Member.builder()
                .socialProvider(socialProvider)
                .socialProviderUid(oidcUserInfo.socialProviderUid())
                .email(oidcUserInfo.email())
                .build();
    }

    public static MemberResDTO.Login toLoginResponse(
            String accessToken,
            String refreshToken,
            Boolean isNewUser,
            Boolean termsAgreed
    ) {
        return MemberResDTO.Login.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .isNewUser(isNewUser)
                .termsAgreed(termsAgreed)
                .build();
    }

    public static MemberResDTO.Nickname toNicknameResponse(
            String nickname
    ) {
        return MemberResDTO.Nickname.builder()
                .nickname(nickname)
                .build();
    }
}
