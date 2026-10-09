package com.kiri.server.domain.member.service;

import com.kiri.server.domain.member.dto.MemberReqDTO;
import com.kiri.server.domain.member.dto.MemberResDTO;
import com.kiri.server.domain.member.enums.SocialProvider;
import com.kiri.server.domain.member.oauth.AbstractOidcProvider;
import com.kiri.server.domain.member.oauth.OidcProviderFactory;
import com.kiri.server.domain.member.oauth.OidcUserInfo;
import com.kiri.server.global.apiPayload.code.AuthErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private OidcProviderFactory oidcProviderFactory;

    @Mock
    private MemberWriter memberWriter;

    @Mock
    private AbstractOidcProvider oidcProvider;

    @InjectMocks
    private MemberService memberService;

    @ParameterizedTest
    @MethodSource("socialProviders")
    @DisplayName("소셜 로그인 성공")
    void googleLoginSuccess(SocialProvider socialProvider) {

        MemberReqDTO.Login request = new MemberReqDTO.Login(socialProvider.name(), "mock-id-token", "test-nonce");
        OidcUserInfo userInfo = new OidcUserInfo(socialProvider.name().toLowerCase() + "-user-1", "test@gmail.com");
        MemberResDTO.Login expected = new MemberResDTO.Login("access-token", "refresh-token", false, true);

        given(oidcProviderFactory.getSocialProvider(socialProvider)).willReturn(oidcProvider);
        given(oidcProvider.verify("mock-id-token", "test-nonce")).willReturn(userInfo);
        given(memberWriter.persist(socialProvider, userInfo)).willReturn(expected);

        MemberResDTO.Login result = memberService.login(request);

        assertThat(result).isEqualTo(expected);

        verify(oidcProvider).verify("mock-id-token", "test-nonce");
        verify(memberWriter).persist(socialProvider, userInfo);
    }

    static Stream<SocialProvider> socialProviders() {
        return Stream.of(
                SocialProvider.GOOGLE,
                SocialProvider.KAKAO,
                SocialProvider.APPLE
        );
    }

    @Test
    @DisplayName("지원하지 않는 provider")
    void unsupportedProvider() {

        MemberReqDTO.Login request = new MemberReqDTO.Login("NAVER", "mock-id-token", "test-nonce");

        assertThatThrownBy(() -> memberService.login(request))
                .isInstanceOf(ProjectException.class)
                .extracting(e -> ((ProjectException) e ).getErrorCode())
                .isEqualTo(AuthErrorCode.UNSUPPORTED_PROVIDER);

        verifyNoInteractions(oidcProviderFactory);
        verifyNoInteractions(memberWriter);
    }

    @Test
    @DisplayName("OIDC 토큰 검증 실패")
    void invalidOidcToken() {

        MemberReqDTO.Login request = new MemberReqDTO.Login("GOOGLE", "invalid-token", "test-nonce");

        given(oidcProviderFactory.getSocialProvider(SocialProvider.GOOGLE))
                .willReturn(oidcProvider);

        given(oidcProvider.verify("invalid-token", "test-nonce"))
                .willThrow(new ProjectException(AuthErrorCode.INVALID_PROVIDER_TOKEN));

        assertThatThrownBy(() -> memberService.login(request))
                .isInstanceOf(ProjectException.class)
                .extracting(e -> ((ProjectException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_PROVIDER_TOKEN);

        verify(oidcProvider).verify("invalid-token", "test-nonce");

        verifyNoInteractions(memberWriter);
    }
}