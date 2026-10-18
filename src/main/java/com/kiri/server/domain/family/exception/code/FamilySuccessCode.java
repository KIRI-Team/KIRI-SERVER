package com.kiri.server.domain.family.exception.code;

import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FamilySuccessCode implements BaseSuccessCode {

    FAMILY_CREATED(HttpStatus.CREATED, "FAMILY201_1", "가족 공간이 생성되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
