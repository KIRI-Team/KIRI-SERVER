package com.kiri.server.domain.member.service;

import com.kiri.server.domain.member.dto.MemberReqDTO;
import com.kiri.server.domain.member.dto.MemberResDTO;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.enums.SocialProvider;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private OidcProviderFactory oidcProviderFactory;

    @Mock
    private MemberWriter memberWriter;

    @Mock
    private AbstractOidcProvider oidcProvider;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberTermRepository memberTermRepository;

    @Mock
    private HashUtil hashUtil;

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

    @Test
    @DisplayName("Refresh Token으로 토큰 재발급 성공")
    void tokenReissueSuccess() {
        Long memberId = 1L;

        String refreshToken = "refresh-token";
        String newAccessToken = "new-access-token";
        String newRefreshToken = "new-refresh-token";

        MemberReqDTO.TokenReissue dto = new MemberReqDTO.TokenReissue(refreshToken);

        Member member = mock(Member.class);
        when(member.getId()).thenReturn(memberId);

        when(jwtProvider.isValid(refreshToken)).thenReturn(true);
        when(jwtProvider.isRefreshToken(refreshToken)).thenReturn(true);
        when(jwtProvider.getMemberIdFromRefreshToken(refreshToken)).thenReturn(memberId);

        when(memberRepository.findByIdForUpdate(memberId)).thenReturn(Optional.of(member));

        when(member.getRefreshTokenHash()).thenReturn("hashed-old-refresh-token");

        when(hashUtil.matches(refreshToken, "hashed-old-refresh-token")).thenReturn(true);

        when(jwtProvider.createAccessToken(memberId)).thenReturn(newAccessToken);
        when(jwtProvider.createRefreshToken(memberId)).thenReturn(newRefreshToken);

        when(hashUtil.hash(newRefreshToken)).thenReturn("hashed-new-refresh-token");

        when(memberTermRepository.areAllRequiredTermsAgreed(memberId)).thenReturn(true);

        MemberResDTO.TokenReissue response = memberService.tokenReissue(dto);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo(newAccessToken);
        assertThat(response.refreshToken()).isEqualTo(newRefreshToken);
        assertThat(response.termsAgreed()).isTrue();

        verify(jwtProvider).isValid(refreshToken);
        verify(jwtProvider).isRefreshToken(refreshToken);
        verify(jwtProvider).getMemberIdFromRefreshToken(refreshToken);

        verify(memberRepository).findByIdForUpdate(memberId);

        verify(hashUtil).matches(refreshToken, "hashed-old-refresh-token");

        verify(jwtProvider).createAccessToken(memberId);
        verify(jwtProvider).createRefreshToken(memberId);

        verify(hashUtil).hash(newRefreshToken);

        verify(member).updateRefreshTokenToHash("hashed-new-refresh-token");

        verify(memberTermRepository).areAllRequiredTermsAgreed(memberId);
    }

    @Test
    @DisplayName("유효하지 않은 Refresh Token이면 재발급 실패")
    void tokenReissueInvalidToken() {
        String refreshToken = "invalid-refresh-token";

        MemberReqDTO.TokenReissue dto = new MemberReqDTO.TokenReissue(refreshToken);

        when(jwtProvider.isValid(refreshToken)).thenReturn(false);

        assertThatThrownBy(() ->
                memberService.tokenReissue(dto)
        )
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_TOKEN);

        verify(jwtProvider).isValid(refreshToken);
        verify(jwtProvider, never()).isRefreshToken(anyString());
        verify(memberRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    @DisplayName("Refresh Token이 아닌 토큰이면 재발급 실패")
    void tokenReissueNotRefreshToken() {
        String token = "access-token";

        MemberReqDTO.TokenReissue dto = new MemberReqDTO.TokenReissue(token);

        when(jwtProvider.isValid(token)).thenReturn(true);
        when(jwtProvider.isRefreshToken(token)).thenReturn(false);

        assertThatThrownBy(() ->
                memberService.tokenReissue(dto)
        )
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_TOKEN);

        verify(jwtProvider).isValid(token);
        verify(jwtProvider).isRefreshToken(token);
        verify(memberRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    @DisplayName("Refresh Token의 회원이 존재하지 않으면 재발급 실패")
    void tokenReissueMemberNotFound() {
        Long memberId = 1L;
        String refreshToken = "refresh-token";

        MemberReqDTO.TokenReissue dto = new MemberReqDTO.TokenReissue(refreshToken);

        when(jwtProvider.isValid(refreshToken)).thenReturn(true);
        when(jwtProvider.isRefreshToken(refreshToken)).thenReturn(true);
        when(jwtProvider.getMemberIdFromRefreshToken(refreshToken)).thenReturn(memberId);

        when(memberRepository.findByIdForUpdate(memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                memberService.tokenReissue(dto)
        )
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.NOT_FOUND);

        verify(memberRepository).findByIdForUpdate(memberId);

        verify(hashUtil, never()).matches(anyString(), anyString());

        verify(jwtProvider, never()).createAccessToken(anyLong());

        verify(jwtProvider, never()).createRefreshToken(anyLong());
    }

    @Test
    @DisplayName("저장된 Refresh Token과 일치하지 않으면 재발급 실패")
    void tokenReissueRefreshTokenMismatch() {
        Long memberId = 1L;

        String refreshToken = "refresh-token";
        String storedHash = "stored-refresh-token-hash";

        MemberReqDTO.TokenReissue dto = new MemberReqDTO.TokenReissue(refreshToken);

        Member member = mock(Member.class);

        when(jwtProvider.isValid(refreshToken)).thenReturn(true);
        when(jwtProvider.isRefreshToken(refreshToken)).thenReturn(true);
        when(jwtProvider.getMemberIdFromRefreshToken(refreshToken)).thenReturn(memberId);

        when(memberRepository.findByIdForUpdate(memberId)).thenReturn(Optional.of(member));

        when(member.getRefreshTokenHash()).thenReturn(storedHash);

        when(hashUtil.matches(refreshToken, storedHash)).thenReturn(false);

        assertThatThrownBy(() ->
                memberService.tokenReissue(dto)
        )
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_TOKEN);

        verify(hashUtil).matches(refreshToken, storedHash);

        verify(jwtProvider, never()).createAccessToken(anyLong());
        verify(jwtProvider, never()).createRefreshToken(anyLong());

        verify(member, never()).updateRefreshTokenToHash(anyString());

        verify(memberTermRepository, never()).areAllRequiredTermsAgreed(anyLong());
    }
}