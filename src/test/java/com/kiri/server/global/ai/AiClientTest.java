package com.kiri.server.global.ai;

import com.kiri.server.global.ai.exception.AiException;
import com.kiri.server.global.ai.exception.code.AiErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiClientTest {

    private static final String BASE_URL = "https://api.openai.com";

    @Test
    @DisplayName("OpenAI Chat Completions로 모델·프롬프트·JSON 모드를 보내고 첫 번째 응답 내용을 돌려준다")
    void generate() {
        Fixture fx = fixture("sk-test");
        fx.server.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andExpect(jsonPath("$.model").value("gpt-6-luna"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("분류해줘"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withSuccess("""
                        {
                          "id": "chatcmpl-1",
                          "object": "chat.completion",
                          "choices": [
                            {"index": 0, "message": {"role": "assistant", "content": "{\\"priceLevel\\": \\"FREE\\"}"}, "finish_reason": "stop"}
                          ],
                          "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(fx.client.generate("분류해줘")).isEqualTo("{\"priceLevel\": \"FREE\"}");
        fx.server.verify();
    }

    @Test
    @DisplayName("API 키가 없으면 요청을 보내지 않고 미설정 오류를 낸다")
    void notConfigured() {
        Fixture fx = fixture("");

        assertThatThrownBy(() -> fx.client.generate("분류해줘"))
                .isInstanceOf(AiException.class)
                .extracting("errorCode").isEqualTo(AiErrorCode.AI_NOT_CONFIGURED);
        fx.server.verify();
    }

    @Test
    @DisplayName("429 응답은 요청 한도 초과 오류로 바꾼다")
    void rateLimited() {
        Fixture fx = fixture("sk-test");
        fx.server.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> fx.client.generate("분류해줘"))
                .isInstanceOf(AiException.class)
                .extracting("errorCode").isEqualTo(AiErrorCode.AI_RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("choices가 비어 있으면 응답 형식 오류를 낸다")
    void emptyChoices() {
        Fixture fx = fixture("sk-test");
        fx.server.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\": []}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fx.client.generate("분류해줘"))
                .isInstanceOf(AiException.class)
                .extracting("errorCode").isEqualTo(AiErrorCode.AI_RESPONSE_INVALID);
    }

    private Fixture fixture(String apiKey) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new AiClient(builder.build(), apiKey, "gpt-6-luna"), server);
    }

    private record Fixture(AiClient client, MockRestServiceServer server) {
    }
}
