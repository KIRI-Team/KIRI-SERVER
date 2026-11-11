package com.kiri.server.admin.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "admin")
public record AdminProperties(
        Duration accessTokenExpiration,
        Duration refreshTokenExpiration,
        List<String> corsAllowedOrigins
) {

    public AdminProperties {
        accessTokenExpiration = accessTokenExpiration == null ? Duration.ofHours(1) : accessTokenExpiration;
        refreshTokenExpiration = refreshTokenExpiration == null ? Duration.ofDays(7) : refreshTokenExpiration;
        corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : List.copyOf(corsAllowedOrigins);
    }
}
