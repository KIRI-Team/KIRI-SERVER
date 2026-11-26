package com.kiri.server.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;

class AiConfigTest {

    @ParameterizedTest
    @DisplayName("주소 끝의 / 와 /v1 은 떼어 내서 요청 경로가 /v1/v1 이 되지 않게 한다")
    @CsvSource({
            "https://api.openai.com, https://api.openai.com",
            "https://apirouter.pleum.ai, https://apirouter.pleum.ai",
            "https://apirouter.pleum.ai/, https://apirouter.pleum.ai",
            "https://apirouter.pleum.ai/v1, https://apirouter.pleum.ai",
            "https://apirouter.pleum.ai/v1/, https://apirouter.pleum.ai"
    })
    void normalize(String input, String expected) {
        assertThat(AiConfig.normalizeBaseUrl(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("주소가 비어 있으면 OpenAI 기본 주소를 쓴다")
    @NullAndEmptySource
    void blankFallsBackToOpenAi(String input) {
        assertThat(AiConfig.normalizeBaseUrl(input)).isEqualTo("https://api.openai.com");
    }
}
