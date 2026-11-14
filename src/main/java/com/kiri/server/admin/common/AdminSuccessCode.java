package com.kiri.server.admin.common;

import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AdminSuccessCode implements BaseSuccessCode {

    OK(HttpStatus.OK, "ADMIN200_1", "요청에 성공했습니다."),
    CREATED(HttpStatus.CREATED, "ADMIN201_1", "생성에 성공했습니다."),
    ACCEPTED(HttpStatus.ACCEPTED, "ADMIN202_1", "요청을 접수했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
