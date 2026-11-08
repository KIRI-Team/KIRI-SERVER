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
    NAME_UPDATED(HttpStatus.OK, "FAMILY200_4", "가족명이 변경되었습니다."),
    ADDRESS_UPDATED(HttpStatus.OK, "FAMILY200_5", "가족 주소가 저장되었습니다."),
    FAMILY_ID_SUCCESS(HttpStatus.OK, "FAMILY200_6", "가족 ID 조회에 성공했습니다."),
    FAMILY_JOINED(HttpStatus.OK, "FAMILY200_7", "가족 가입에 성공했습니다."),
    FAMILY_INVITE_TOKEN_INFO_SUCCESS(HttpStatus.OK, "FAMILY200_8", "가족 초대 정보를 조회했습니다."),
    FAMILY_INFO_SUCCESS(HttpStatus.OK, "FAMILY200_9", "가족 정보 조회에 성공했습니다."),
    FAMILY_CREATED(HttpStatus.CREATED, "FAMILY201_1", "가족 공간이 생성되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
