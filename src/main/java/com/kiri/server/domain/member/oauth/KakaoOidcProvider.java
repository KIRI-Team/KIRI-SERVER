package com.kiri.server.domain.member.oauth;

import com.kiri.server.domain.member.enums.SocialProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KakaoOidcProvider extends AbstractOidcProvider{

    private final String clientId;
    private final String jwksUri;
    private final String issuer;

    public KakaoOidcProvider(
            @Value("${kakao.client-id}") String clientIds,
            @Value("${kakao.jwks-uri}") String jwksUri,
            @Value("${kakao.issuer}") String issuer,
            OidcJwksClient oidcJwksClient
    ) {
        super(oidcJwksClient);
        this.clientId = clientIds;
        this.jwksUri = jwksUri;
        this.issuer = issuer;
    }

    @Override
    protected List<String> getClientIds() {
        return List.of(clientId);
    }

    @Override
    protected String getIssuer() {
        return issuer;
    }

    @Override
    protected String getJwksUri() {
        return jwksUri;
    }

    @Override
    public SocialProvider getSocialProvider() {
        return SocialProvider.KAKAO;
    }
}
