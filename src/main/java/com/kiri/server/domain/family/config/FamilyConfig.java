package com.kiri.server.domain.family.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FamilyInviteProperties.class)
public class FamilyConfig {
}
