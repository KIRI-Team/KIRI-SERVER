package com.kiri.server.global.security.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private static final String SECRET = "test-secret-key-for-jwt-must-be-at-least-32-bytes";

    private final JwtProvider jwtProvider = createProvider(SECRET, Duration.ofMinutes(30));

    @Test
    @DisplayName("생성한 토큰에서 memberId를 꺼낼 수 있다.")
    void createAndParse() {
        String token = jwtProvider.createAccessToken(1L);

        assertThat(jwtProvider.getMemberIdFromAccessToken(token)).isEqualTo(1L);
    }

    @Test
    @DisplayName("만료된 토큰은 ExpiredJwtException이 발생한다.")
    void expiredToken() {
        JwtProvider expiredProvider = createProvider(SECRET, Duration.ofSeconds(-1));
        String token = expiredProvider.createAccessToken(1L);

        assertThatThrownBy(() -> jwtProvider.getMemberIdFromAccessToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 JwtException이 발생한다.")
    void invalidSignature() {
        JwtProvider otherProvider = createProvider("other-secret-key-for-jwt-must-be-at-least-32-bytes", Duration.ofMinutes(30));
        String token = otherProvider.createAccessToken(1L);

        assertThatThrownBy(() -> jwtProvider.getMemberIdFromAccessToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("refresh 토큰은 access 토큰으로 사용할 수 없다.")
    void refreshTokenRejected() {
        String token = jwtProvider.createRefreshToken(1L);

        assertThatThrownBy(() -> jwtProvider.getMemberIdFromAccessToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("JWT 형식이 아닌 토큰은 JwtException이 발생한다.")
    void malformedToken() {
        assertThatThrownBy(() -> jwtProvider.getMemberIdFromAccessToken("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    private JwtProvider createProvider(String secret, Duration accessExpiration) {
        return new JwtProvider(new JwtProperties(secret, accessExpiration, Duration.ofDays(14)));
    }
}
