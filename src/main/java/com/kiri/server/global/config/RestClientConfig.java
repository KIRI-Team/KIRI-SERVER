package com.kiri.server.global.config;

import com.kiri.server.domain.external.client.tourapi.TourApiProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

@Configuration
@EnableConfigurationProperties(TourApiProperties.class)
public class RestClientConfig {

    @Bean
    public RestClient tourApiRestClient(TourApiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .uriBuilderFactory(strictUriBuilderFactory(properties.baseUrl()))
                .requestFactory(requestFactory)
                .build();
    }

    public static DefaultUriBuilderFactory strictUriBuilderFactory(String baseUrl) {
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory(baseUrl);
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.TEMPLATE_AND_VALUES);
        return factory;
    }
}
