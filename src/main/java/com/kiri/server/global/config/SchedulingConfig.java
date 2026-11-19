package com.kiri.server.global.config;

import com.kiri.server.domain.mission.config.MissionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@Configuration
@EnableConfigurationProperties(MissionProperties.class)
public class SchedulingConfig {
}
