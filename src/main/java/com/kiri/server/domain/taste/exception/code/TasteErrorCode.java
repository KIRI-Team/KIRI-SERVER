package com.kiri.server.domain.taste.exception.code;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TasteErrorCode implements BaseErrorCode {

    INVALID_INDOOR_OUTDOOR(HttpStatus.BAD_REQUEST, "TASTE400_1", "공간은 INDOOR, OUTDOOR 중 하나여야 합니다."),
    DUPLICATE_CATEGORY(HttpStatus.BAD_REQUEST, "TASTE400_2", "선호 활동은 중복해서 선택할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
