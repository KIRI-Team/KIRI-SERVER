package com.kiri.server.domain.family.exception.code;

import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FamilySuccessCode implements BaseSuccessCode {

    INVITE_REGENERATED(HttpStatus.OK, "FAMILY200_1", "가족 초대 정보가 생성되었습니다."),
    INVITE_DELETED(HttpStatus.OK, "FAMILY200_2", "가족 초대 정보가 삭제되었습니다."),
    INVITE_FOUND(HttpStatus.OK, "FAMILY200_3", "가족 초대 정보를 조회했습니다."),
    FAMILY_CREATED(HttpStatus.CREATED, "FAMILY201_1", "가족 공간이 생성되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
