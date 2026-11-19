package com.kiri.server.global.ai.exception.code;

import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AiSuccessCode implements BaseSuccessCode {

    CLASSIFICATION_SUCCESS(HttpStatus.OK, "AI200_1", "취향 분류에 성공했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
