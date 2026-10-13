package com.kiri.server.domain.external.exception.code;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ExternalErrorCode implements BaseErrorCode {

    SERVICE_KEY_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "EXTERNAL500_1", "외부 API 인증키가 설정되지 않았습니다."),

    SERVICE_KEY_INVALID(HttpStatus.BAD_GATEWAY, "EXTERNAL502_1", "외부 API 인증키가 등록되지 않았거나 사용할 수 없습니다."),
    CALL_FAILED(HttpStatus.BAD_GATEWAY, "EXTERNAL502_2", "외부 API 호출에 실패했습니다."),
    ERROR_RESPONSE(HttpStatus.BAD_GATEWAY, "EXTERNAL502_3", "외부 API가 오류를 응답했습니다."),
    INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "EXTERNAL502_4", "외부 API 응답 형식을 해석할 수 없습니다."),

    LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "EXTERNAL429_1", "외부 API 일일 호출 한도를 초과했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
