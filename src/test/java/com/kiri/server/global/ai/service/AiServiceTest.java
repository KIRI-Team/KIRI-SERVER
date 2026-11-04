package com.kiri.server.global.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.question.dto.PreferenceClassification;
import com.kiri.server.domain.question.enums.TasteType;
import com.kiri.server.domain.taste.enums.PriceLevel;
import com.kiri.server.global.ai.AiClient;
import com.kiri.server.global.ai.exception.AiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiServiceTest {

    private final AiClient aiClient = mock(AiClient.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AiService aiService = new AiService(aiClient, objectMapper);

    @Test
    @DisplayName("CATEGORY 응답을 정상적으로 파싱한다")
    void parseCategoryResponse() {
        when(aiClient.generate(anyString()))
                .thenReturn("""
                        {
                          "categories": ["EXHIBITION", "POPUP"]
                        }
                        """);

        PreferenceClassification result = aiService.classify(TasteType.CATEGORY, "주말에 어떤 활동을 하고 싶나요?", "전시회나 팝업스토어를 구경하고 싶어요.");

        assertThat(result.categories()).containsExactly(Category.EXHIBITION, Category.POPUP);

        assertThat(result.indoorOutdoor()).isNull();
        assertThat(result.durationLevel()).isNull();
        assertThat(result.priceLevel()).isNull();
        assertThat(result.activityLevel()).isNull();
    }

    @Test
    @DisplayName("PRICE 응답을 정상적으로 파싱한다.")
    void parsePriceResponse() {
        when(aiClient.generate(anyString()))
                .thenReturn("""
                        {
                          "priceLevel": "FREE"
                        }
                        """);

        PreferenceClassification result = aiService.classify(TasteType.PRICE, "어떤 가격대의 활동을 선호하나요?", "가능하면 무료인 활동이 좋아요.");

        assertThat(result.priceLevel()).isEqualTo(PriceLevel.FREE);

        assertThat(result.categories()).isEmpty();
    }

    @Test
    @DisplayName("JSON 코드블록 형태의 응답도 정상적으로 파싱한다.")
    void parseJsonCodeBlockResponse() {
        when(aiClient.generate(anyString()))
                .thenReturn("""
                        ```json
                        {
                          "priceLevel": "FREE"
                        }
                        ```
                        """);

        PreferenceClassification result = aiService.classify(TasteType.PRICE, "어떤 가격대의 활동을 선호하나요?", "무료가 좋아요.");

        assertThat(result.priceLevel()).isEqualTo(PriceLevel.FREE);
    }

    @Test
    @DisplayName("잘못된 JSON 응답이 반환되면 AI 응답 오류를 발생시킨다.")
    void throwExceptionWhenResponseIsInvalidJson() {
        when(aiClient.generate(anyString()))
                .thenReturn("""
                        {
                          "priceLevel":
                        """);

        assertThatThrownBy(() -> aiService.classify(TasteType.PRICE, "어떤 가격대의 활동을 선호하나요?", "무료가 좋아요.")).isInstanceOf(AiException.class);
    }
}