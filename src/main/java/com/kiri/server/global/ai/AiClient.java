package com.kiri.server.global.ai;

import com.kiri.server.global.ai.exception.AiException;
import com.kiri.server.global.ai.exception.code.AiErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * OpenAI Chat Completions API 호출 클라이언트.
 * 모델은 openai.model(기본 gpt-6-luna), 키는 OPENAI_API_KEY 환경변수로 받는다.
 * 키가 비어 있어도 서버는 뜨고, 실제 호출 시점에만 실패한다.
 */
@Slf4j
@Component
public class AiClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public AiClient(
            @Qualifier("aiRestClient") RestClient restClient,
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.model}") String model
    ) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    public String generate(String prompt) {

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OPENAI_API_KEY가 설정되지 않아 AI 요청을 보내지 않음");
            throw new AiException(AiErrorCode.AI_NOT_CONFIGURED);
        }

        AiRequest request = AiRequest.of(model, prompt);

        try {
            AiResponse response = restClient.post()
                    .uri("/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(request)
                    .retrieve()
                    .body(AiResponse.class);

            if (response == null) {
                throw new AiException(AiErrorCode.AI_RESPONSE_EMPTY);
            }

            return response.getText();
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new AiException(AiErrorCode.AI_RATE_LIMIT_EXCEEDED, e);
        } catch (RestClientException e) {
            throw new AiException(AiErrorCode.AI_REQUEST_FAILED, e);
        }
    }
}
