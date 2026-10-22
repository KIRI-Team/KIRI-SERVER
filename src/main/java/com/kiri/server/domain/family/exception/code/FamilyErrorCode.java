package com.kiri.server.domain.family.exception.code;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FamilyErrorCode implements BaseErrorCode {

    // 409
    DUPLICATE_NAME(HttpStatus.CONFLICT, "FAMILY409_1", "이미 존재하는 가족 이름입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
