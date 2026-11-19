package com.kiri.server.domain.image.exception.code;

import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ImageSuccessCode implements BaseSuccessCode {

    PRESIGNED_URL_ISSUED(HttpStatus.OK, "IMAGE200_1", "Presigned URL 발급 성공");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
