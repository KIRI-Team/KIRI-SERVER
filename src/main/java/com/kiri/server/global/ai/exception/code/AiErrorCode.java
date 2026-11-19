package com.kiri.server.global.ai.exception.code;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AiErrorCode implements BaseErrorCode {

    AI_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AI429_1", "AI 요청 횟수를 초과했습니다. 한도 갱신 후 다시 시도해주세요."),
    AI_REQUEST_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI500_1", "AI 요청에 실패했습니다."),
    AI_RESPONSE_EMPTY(HttpStatus.BAD_GATEWAY, "AI502_1", "AI 응답이 없습니다."),
    AI_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "AI502_2", "AI 응답 형식이 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
