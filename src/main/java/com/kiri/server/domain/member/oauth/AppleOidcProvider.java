package com.kiri.server.domain.member.oauth;

import com.kiri.server.domain.member.enums.SocialProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AppleOidcProvider extends AbstractOidcProvider {

    private final String clientId;
    private final String jwksUri;
    private final String issuer;

    public AppleOidcProvider(
            @Value("${apple.client-id}") String clientId,
            @Value("${apple.jwks-uri}") String jwksUri,
            @Value("${apple.issuer}") String issuer,
            OidcJwksClient oidcJwksClient
    ) {
        super(oidcJwksClient);
        this.clientId = clientId;
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
        return SocialProvider.APPLE;
    }
}
