package com.kiri.server.domain.term.exception.code;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TermErrorCode implements BaseErrorCode {
    REQUIRED_NOT_AGREED(HttpStatus.BAD_REQUEST, "TERM400_1", "필수 약관에 모두 동의해야 합니다."),

    NOT_FOUND(HttpStatus.NOT_FOUND, "TERM404_1", "존재하지 않는 약관입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
