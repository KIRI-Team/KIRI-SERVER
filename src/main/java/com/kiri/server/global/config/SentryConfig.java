package com.kiri.server.global.config;

import com.kiri.server.global.apiPayload.exception.ProjectException;
import io.sentry.SentryOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SentryConfig {
    @Bean
    public SentryOptions.BeforeSendCallback beforeSendCallback() {
        return (event, hint) -> {
            if (event.getThrowable() instanceof ProjectException e
                    && e.getErrorCode().getHttpStatus().is4xxClientError()) {
                return null;
            }
            return event;
        };
    }
}
