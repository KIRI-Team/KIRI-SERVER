package com.kiri.server.domain.member.oauth;

import com.kiri.server.domain.member.enums.SocialProvider;
import com.kiri.server.global.apiPayload.code.AuthErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AbstractOidcProviderTest {

    private static final String CLIENT_ID = "test-client-id";
    private static final String ISSUER = "http://test-issuer.com";
    private static final String JWKS_URI = "http://test-jwks.com";
    private static final String KID = "test-kid";
    private static final String NONCE = "test-nonce";

    @Mock
    private OidcJwksClient oidcJwksClient;

    private TestOidcProvider oidcProvider;

    private RSAKey rsaKey;

    @BeforeEach
    void setUp() throws Exception {
        rsaKey = new RSAKeyGenerator(2048)
                .keyID(KID)
                .generate();

        oidcProvider = new TestOidcProvider(oidcJwksClient);

        given(oidcJwksClient.getPublicKey(JWKS_URI, KID))
                .willReturn(rsaKey.toRSAPublicKey());
    }

    @Test
    @DisplayName("정상적인 OIDC 토큰 검증 성공")
    void verifySuccess() throws Exception {

        String token = createToken(
                ISSUER,
                CLIENT_ID,
                new Date(System.currentTimeMillis() + 60_000),
                "test-user-1",
                NONCE
        );

        OidcUserInfo result = oidcProvider.verify(token, NONCE);

        assertThat(result.socialProviderUid()).isEqualTo("test-user-1");
        assertThat(result.email()).isEqualTo("test@gmail.com");
    }

    @Test
    @DisplayName("OIDC 토큰의 서명이 유효하지 않으면 검증 실패")
    void invalidSignature() throws Exception {

        RSAKey anotherRsaKey = new RSAKeyGenerator(2048)
                .keyID(KID)
                .generate();

        String token = createToken(
                anotherRsaKey,
                ISSUER,
                CLIENT_ID,
                new Date(System.currentTimeMillis() + 60_000),
                "test-user-1",
                NONCE
        );

        assertInvalidProviderToken(token, NONCE);
    }

    @Test
    @DisplayName("OIDC 토큰의 issuer가 일치하지 않으면 검증 실패")
    void invalidIssuer() throws Exception {

        String token = createToken(
                "https://wrong-issuer.com",
                CLIENT_ID,
                new Date(System.currentTimeMillis() + 60_000),
                "test-user-1",
                NONCE
        );

        assertInvalidProviderToken(token, NONCE);
    }

    @Test
    @DisplayName("OIDC 토큰의 audience가 일치하지 않으면 검증 실패")
    void invalidAudience() throws Exception {

        String token = createToken(
                ISSUER,
                "wrong-client-id",
                new Date(System.currentTimeMillis() + 60_000),
                "test-user-1",
                NONCE
        );

        assertInvalidProviderToken(token, NONCE);
    }

    @Test
    @DisplayName("OIDC 토큰이 만료되었으면 검증 실패")
    void expiredToken() throws Exception {

        String token = createToken(
                ISSUER,
                CLIENT_ID,
                new Date(System.currentTimeMillis() - 60_000),
                "test-user-1",
                NONCE
        );

        assertInvalidProviderToken(token, NONCE);
    }

    @Test
    @DisplayName("OIDC 토큰에 subject가 없으면 검증 실패")
    void missingSubject() throws Exception {

        String token = createToken(
                ISSUER,
                CLIENT_ID,
                new Date(System.currentTimeMillis() + 60_000),
                null,
                NONCE
        );

        assertInvalidProviderToken(token, NONCE);
    }

    @Test
    @DisplayName("OIDC 토큰의 nonce가 요청 nonce와 일치하지 않으면 검증 실패")
    void invalidNonce() throws Exception {

        String token = createToken(
                ISSUER,
                CLIENT_ID,
                new Date(System.currentTimeMillis() + 60_000),
                "test-user-1",
                "wrong-nonce"
        );

        assertInvalidProviderToken(token, NONCE);
    }

    private String createToken(String issuer, String audience, Date expiration, String subject, String nonce) throws Exception {

        return createToken(rsaKey, issuer, audience, expiration, subject, nonce);
    }

    private String createToken(RSAKey signingKey, String issuer, String audience, Date expiration, String subject, String nonce) throws Exception {

        JWTClaimsSet.Builder claimsBuilder =
                new JWTClaimsSet.Builder()
                        .issuer(issuer)
                        .audience(audience)
                        .expirationTime(expiration)
                        .claim("email", "test@gmail.com")
                        .claim("nonce", nonce);

        if (subject != null) {
            claimsBuilder.subject(subject);
        }

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(KID)
                        .build(),
                claimsBuilder.build()
        );

        signedJWT.sign(new RSASSASigner(signingKey.toPrivateKey()));

        return signedJWT.serialize();
    }

    private void assertInvalidProviderToken(String token, String nonce) {

        assertThatThrownBy(
                () -> oidcProvider.verify(token, nonce)
        )
                .isInstanceOf(ProjectException.class)
                .extracting(e -> ((ProjectException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_PROVIDER_TOKEN);
    }

    private static class TestOidcProvider extends AbstractOidcProvider {

        protected TestOidcProvider(OidcJwksClient oidcJwksClient) {
            super(oidcJwksClient);
        }

        @Override
        protected String getClientId() {
            return CLIENT_ID;
        }

        @Override
        protected String getIssuer() {
            return ISSUER;
        }

        @Override
        protected String getJwksUri() {
            return JWKS_URI;
        }

        @Override
        public SocialProvider getSocialProvider() {
            return SocialProvider.GOOGLE;
        }
    }
}