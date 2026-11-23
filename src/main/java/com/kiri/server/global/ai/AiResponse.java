package com.kiri.server.global.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kiri.server.global.ai.exception.AiException;
import com.kiri.server.global.ai.exception.code.AiErrorCode;

import java.util.List;

/**
 * OpenAI Chat Completions 응답 중 분류에 필요한 choices[0].message.content만 읽는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiResponse(List<Choice> choices) {

    public String getText() {

        if (choices == null || choices.isEmpty()) {
            throw new AiException(AiErrorCode.AI_RESPONSE_INVALID);
        }

        Message message = choices.get(0).message();

        if (message == null || message.content() == null || message.content().isBlank()) {
            throw new AiException(AiErrorCode.AI_RESPONSE_INVALID);
        }

        return message.content();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String role, String content) {}
}
