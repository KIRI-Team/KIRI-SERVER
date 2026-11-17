package com.kiri.server.domain.family.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "family.invite")
public record FamilyInviteProperties(String baseUrl) {
}
