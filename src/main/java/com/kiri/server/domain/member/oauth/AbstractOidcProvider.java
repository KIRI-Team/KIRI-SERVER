package com.kiri.server.domain.member.oauth;

import com.kiri.server.domain.member.enums.SocialProvider;
import com.kiri.server.global.apiPayload.code.AuthErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;

import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;

@Slf4j
public abstract class AbstractOidcProvider {

    protected final OidcJwksClient oidcJwksClient;

    protected AbstractOidcProvider(OidcJwksClient oidcJwksClient) {
        this.oidcJwksClient = oidcJwksClient;
    }

    protected abstract List<String> getClientIds();

    protected abstract String getIssuer();

    protected abstract String getJwksUri();

    public abstract SocialProvider getSocialProvider();

    protected boolean isValidIssuer(String issuer) {
        return getIssuer().equals(issuer);
    }

    protected boolean isValidAudience(List<String> audience) {
        return audience != null &&
                audience.stream().anyMatch(getClientIds()::contains);
    }

    public final OidcUserInfo verify(String socialProviderToken, String nonce) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(socialProviderToken);
            String kid = signedJWT.getHeader().getKeyID();

            if (kid == null) {
                log.warn("[OidcVerify] socialProvider={} 검증 실패: kid 없음", getSocialProvider());
                throw new ProjectException(AuthErrorCode.INVALID_PROVIDER_TOKEN);
            }

            RSAPublicKey publicKey = oidcJwksClient.getPublicKey(getJwksUri(), kid);
            JWSVerifier verifier = new RSASSAVerifier(publicKey);

            if (!signedJWT.verify(verifier)) {
                log.warn("[OidcVerify] socialProvider={} 검증 실패: 서명 불일치", getSocialProvider());
                throw new ProjectException(AuthErrorCode.INVALID_PROVIDER_TOKEN);
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            if (!isValidIssuer(claims.getIssuer())) {
                log.warn("[OidcVerify] socialProvider={} 검증 실패: issuer 불일치", getSocialProvider());
                throw new ProjectException(AuthErrorCode.INVALID_PROVIDER_TOKEN);
            }

            if (!isValidAudience(claims.getAudience())) {
                log.warn("[OidcVerify] socialProvider={} 검증 실패: audience 불일치", getSocialProvider());
                throw new ProjectException(AuthErrorCode.INVALID_PROVIDER_TOKEN);
            }

            Date expiration = claims.getExpirationTime();

            if (expiration == null || expiration.before(new Date())) {
                log.warn("[OidcVerify] socialProvider={} 검증 실패: 토큰 만료", getSocialProvider());
                throw new ProjectException(AuthErrorCode.INVALID_PROVIDER_TOKEN);
            }

            String tokenNonce = claims.getStringClaim("nonce");

            if (tokenNonce == null || !tokenNonce.equals(nonce)) {
                log.warn("[OidcVerify] socialProvider={} 검증 실패: nonce 불일치", getSocialProvider());
                throw new ProjectException(AuthErrorCode.INVALID_PROVIDER_TOKEN);
            }

            String subject = claims.getSubject();

            if (subject == null) {
                log.warn("[OidcVerify] socialProvider={} 검증 실패: subject 없음", getSocialProvider());
                throw new ProjectException(AuthErrorCode.INVALID_PROVIDER_TOKEN);
            }

            String email = claims.getStringClaim("email");

            log.info("[OidcVerify] socialProvider={} 검증 성공", getSocialProvider());
            return new OidcUserInfo(subject, email);

        } catch (ProjectException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[OidcVerify] socialProvider={} 검증 실패: {}", getSocialProvider(), e.getClass().getSimpleName());
            throw new ProjectException(AuthErrorCode.INVALID_PROVIDER_TOKEN, e);
        }
    }
}
