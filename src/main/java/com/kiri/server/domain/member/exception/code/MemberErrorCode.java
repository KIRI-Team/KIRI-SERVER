package com.kiri.server.domain.member.exception.code;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements BaseErrorCode {

    // 400 Bad Request
    BANNED_NICKNAME(HttpStatus.BAD_REQUEST, "MEMBER400_1", "닉네임에 사용할 수 없는 표현이 포함되어 있습니다."),

    // 404 Not Found
    NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER404_1", "존재하지 않는 회원입니다."),

    // 409 Conflict
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "MEMBER409_1", "이미 사용 중인 닉네임입니다."),
    ALREADY_WITHDRAWN(HttpStatus.CONFLICT, "MEMBER409_2","이미 탈퇴한 회원입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
