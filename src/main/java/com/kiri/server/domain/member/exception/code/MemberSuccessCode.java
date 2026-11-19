package com.kiri.server.domain.member.exception.code;

import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberSuccessCode implements BaseSuccessCode {

    NICKNAME_SET(HttpStatus.OK, "MEMBER200_1", "닉네임이 설정되었습니다."),
    FAMILY_ROLE_SET(HttpStatus.OK, "MEMBER200_2", "가족 역할이 설정되었습니다."),
    DELETE_SUCCESS(HttpStatus.OK, "MEMBER200_3", "회원 탈퇴가 완료되었습니다."),
    MY_INFO_FOUND(HttpStatus.OK, "MEMBER200_4", "내 정보 조회에 성공했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
