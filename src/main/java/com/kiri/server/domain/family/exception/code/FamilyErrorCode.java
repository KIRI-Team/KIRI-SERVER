package com.kiri.server.domain.family.exception.code;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FamilyErrorCode implements BaseErrorCode {

    // 403
    FORBIDDEN(HttpStatus.FORBIDDEN, "FAMILY403_1", "해당 가족에 대한 권한이 없습니다."),

    // 404
    NOT_FOUND(HttpStatus.NOT_FOUND, "FAMILY404_1", "존재하지 않는 가족입니다."),

    // 409
    DUPLICATE_NAME(HttpStatus.CONFLICT, "FAMILY409_1", "이미 존재하는 가족 이름입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
