package com.kiri.server.domain.member.oauth;

import com.kiri.server.domain.member.enums.SocialProvider;
import com.kiri.server.global.apiPayload.code.AuthErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OidcProviderFactory {

    private final Map<SocialProvider, AbstractOidcProvider> socialProviders;

    public OidcProviderFactory(List<AbstractOidcProvider> oidcProviders) {
        this.socialProviders = oidcProviders.stream()
                .collect(Collectors.toMap(
                        AbstractOidcProvider::getSocialProvider,
                        Function.identity()
                ));
    }

    public AbstractOidcProvider getSocialProvider(SocialProvider socialProvider) {
        AbstractOidcProvider oidcProvider = socialProviders.get(socialProvider);

        if (oidcProvider == null) {
            throw new ProjectException(AuthErrorCode.UNSUPPORTED_PROVIDER);
        }

        return oidcProvider;
    }
}
