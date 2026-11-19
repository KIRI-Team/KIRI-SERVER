package com.kiri.server.global.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiri.server.domain.question.dto.PreferenceClassification;
import com.kiri.server.domain.question.enums.TasteType;
import com.kiri.server.global.ai.AiClient;
import com.kiri.server.global.ai.exception.AiException;
import com.kiri.server.global.ai.exception.code.AiErrorCode;
import com.kiri.server.global.ai.factory.PromptFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public PreferenceClassification classify(TasteType tasteType, String question, String answer) {

        String prompt = PromptFactory.preferenceClassification(tasteType, question, answer);

        String response = aiClient.generate(prompt);

        return parseClassification(response);
    }

    private PreferenceClassification parseClassification(String response) {
        try {
            String json = cleanJson(response);

            PreferenceClassification classification = objectMapper.readValue(json, PreferenceClassification.class);

            return new PreferenceClassification(
                    classification.categories() == null ? List.of() : classification.categories(),
                    classification.indoorOutdoor(),
                    classification.durationLevel(),
                    classification.priceLevel(),
                    classification.activityLevel()
            );

        } catch (JsonProcessingException e) {
            log.error("AI 응답 JSON 파싱 실패. response={}", response, e);
            throw new AiException(AiErrorCode.AI_RESPONSE_INVALID);
        }
    }

    private String cleanJson(String response) {
        return response
                .replace("```json", "")
                .replace("```", "")
                .trim();
    }
}
