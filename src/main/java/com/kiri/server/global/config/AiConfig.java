package com.kiri.server.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AiConfig {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com";

    /**
     * OpenAI 호환 API 주소. 기본은 OpenAI이고, PleumRouter 같은 호환 중계 서비스를 쓰려면 OPENAI_BASE_URL로 바꾼다.
     * 요청 경로(/v1/chat/completions)는 AiClient가 붙이므로, 주소 끝에 /v1을 넣어도 한 번만 쓰이도록 떼어 낸다.
     */
    @Bean
    public RestClient aiRestClient(@Value("${openai.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);

        return RestClient.builder()
                .baseUrl(normalizeBaseUrl(baseUrl))
                .requestFactory(factory)
                .build();
    }

    static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return DEFAULT_BASE_URL;
        }
        String url = baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/v1")) {
            url = url.substring(0, url.length() - 3);
        }
        return url;
    }
}
