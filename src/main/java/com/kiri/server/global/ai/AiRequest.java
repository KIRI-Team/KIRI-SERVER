package com.kiri.server.global.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OpenAI Chat Completions 요청 본문.
 * 분류 결과를 JSON으로 파싱하므로 JSON 모드(response_format: json_object)로 요청한다.
 */
public record AiRequest(
        String model,
        List<Message> messages,
        @JsonProperty("response_format") ResponseFormat responseFormat
) {

    public static AiRequest of(String model, String prompt) {
        return new AiRequest(
                model,
                List.of(new Message("user", prompt)),
                new ResponseFormat("json_object")
        );
    }

    public record Message(String role, String content) {}

    public record ResponseFormat(String type) {}
}
