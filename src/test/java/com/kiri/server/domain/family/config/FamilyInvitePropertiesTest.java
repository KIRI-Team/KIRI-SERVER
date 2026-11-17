package com.kiri.server.domain.family.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FamilyInvitePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FamilyConfig.class);

    @Test
    @DisplayName("family.invite.base-url 값이 FamilyInviteProperties에 바인딩된다")
    void bindsBaseUrl() {
        contextRunner
                .withPropertyValues("family.invite.base-url=https://custom.example.com/invite")
                .run(context -> assertThat(context.getBean(FamilyInviteProperties.class).baseUrl())
                        .isEqualTo("https://custom.example.com/invite"));
    }

    @Test
    @DisplayName("application.yaml의 초대 링크 base URL은 홈페이지 도메인이다")
    void applicationYamlBaseUrl() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));
        StandardEnvironment environment = new StandardEnvironment();
        sources.forEach(environment.getPropertySources()::addLast);

        FamilyInviteProperties properties = Binder.get(environment)
                .bind("family.invite", FamilyInviteProperties.class)
                .get();

        assertThat(properties.baseUrl()).isEqualTo("https://www.halo-official.co.kr/invite");
    }
}
