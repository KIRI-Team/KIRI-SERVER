package com.kiri.server.domain.mission.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mission")
public record MissionProperties(Boolean autoPublish) {

    public MissionProperties {
        autoPublish = autoPublish != null && autoPublish;
    }
}
