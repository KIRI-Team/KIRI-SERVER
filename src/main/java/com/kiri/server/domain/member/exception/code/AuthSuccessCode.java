package com.kiri.server.domain.member.exception.code;

import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthSuccessCode implements BaseSuccessCode {

    LOGIN_SUCCESS(HttpStatus.OK,
            "AUTH200_1",
            "로그인 성공"),
    TOKEN_REISSUE_SUCCESS(HttpStatus.OK,
            "AUTH200_2",
            "토큰 재발급 성공"),
    LOGOUT_SUCCESS(HttpStatus.OK,
            "AUTH200_3",
            "로그아웃 성공")
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
