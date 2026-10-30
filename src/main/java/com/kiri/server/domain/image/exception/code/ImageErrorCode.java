package com.kiri.server.domain.image.exception.code;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ImageErrorCode implements BaseErrorCode {

    UNSUPPORTED_CONTENT_TYPE(HttpStatus.BAD_REQUEST, "IMAGE400_1", "지원하지 않는 이미지 형식입니다."),

    INVALID_IMAGE_KEY(HttpStatus.BAD_REQUEST, "IMAGE400_2", "유효하지 않은 이미지 키입니다."),

    IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "IMAGE400_3", "이미지 용량이 너무 큽니다."),

    IMAGE_NOT_UPLOADED(HttpStatus.NOT_FOUND, "IMAGE404_1", "업로드된 이미지를 찾을 수 없습니다."),

    STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "IMAGE500_1", "이미지 저장소 처리 중 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
