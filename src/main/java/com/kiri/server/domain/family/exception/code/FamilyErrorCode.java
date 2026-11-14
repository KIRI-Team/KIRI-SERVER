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
    INVALID_INVITE_CODE(HttpStatus.NOT_FOUND, "FAMILY404_2", "유효하지 않은 초대 코드입니다."),
    INVALID_INVITE_TOKEN(HttpStatus.NOT_FOUND, "FAMILY404_3", "유효하지 않은 초대 토큰입니다."),

    // 409
    DUPLICATE_NAME(HttpStatus.CONFLICT, "FAMILY409_1", "이미 존재하는 가족 이름입니다."),
    ALREADY_JOINED(HttpStatus.CONFLICT, "FAMILY409_2", "이미 다른 가족에 가입되어 있습니다."),

    // 410
    EXPIRED_INVITE_CODE(HttpStatus.GONE, "FAMILY410_1", "초대 코드가 만료되었습니다."),
    EXPIRED_INVITE_TOKEN(HttpStatus.GONE, "FAMILY410_2", "초대 토큰이 만료되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
