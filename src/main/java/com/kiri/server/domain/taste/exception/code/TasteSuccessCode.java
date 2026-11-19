package com.kiri.server.domain.taste.exception.code;

import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TasteSuccessCode implements BaseSuccessCode {

    GET_SUCCESS(HttpStatus.OK, "TASTE200_1", "내 취향 조회 성공"),
    SAVE_SUCCESS(HttpStatus.OK, "TASTE200_2", "내 취향 저장 성공");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
