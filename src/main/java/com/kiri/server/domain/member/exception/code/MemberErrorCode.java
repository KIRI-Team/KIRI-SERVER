package com.kiri.server.domain.member.exception.code;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements BaseErrorCode {

    // 404 Not Found
    NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER404_1", "존재하지 않는 회원입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "MEMBER409_1", "이미 사용 중인 닉네임입니다."),
    ALREADY_WITHDRAWN(HttpStatus.CONFLICT, "MEMBER409_2","이미 탈퇴한 회원입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
