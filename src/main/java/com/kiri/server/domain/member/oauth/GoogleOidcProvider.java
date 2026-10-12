package com.kiri.server.domain.member.oauth;

import com.kiri.server.domain.member.enums.SocialProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GoogleOidcProvider extends AbstractOidcProvider{

    private final List<String> clientIds;
    private final String jwksUri;
    private final String issuer;

    public GoogleOidcProvider(
            @Value("${google.client-ids}") List<String> clientIds,
            @Value("${google.jwks-uri}") String jwksUri,
            @Value("${google.issuer}") String issuer,
            OidcJwksClient oidcJwksClient
    ) {
        super(oidcJwksClient);
        this.clientIds = clientIds;
        this.jwksUri = jwksUri;
        this.issuer = issuer;
    }

    @Override
    protected List<String> getClientIds() {
        return clientIds;
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
        return SocialProvider.GOOGLE;
    }

    @Override
    protected boolean isValidIssuer(String issuer) {
        return "accounts.google.com".equals(issuer) || "https://accounts.google.com".equals(issuer);
    }
}
