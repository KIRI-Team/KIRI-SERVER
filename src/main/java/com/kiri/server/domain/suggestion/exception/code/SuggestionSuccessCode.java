package com.kiri.server.domain.suggestion.exception.code;

import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SuggestionSuccessCode implements BaseSuccessCode {

    SUGGESTION_CREATED(HttpStatus.CREATED, "SUGGESTION201_1", "건의 작성에 성공했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
