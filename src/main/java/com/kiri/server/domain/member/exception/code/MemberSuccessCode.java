package com.kiri.server.domain.member.exception.code;

import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberSuccessCode implements BaseSuccessCode {

    NICKNAME_SET(HttpStatus.OK, "MEMBER200_1", "닉네임이 설정되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
