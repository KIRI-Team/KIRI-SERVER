package com.kiri.server.domain.bucket.exception.code;

import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BucketSuccessCode implements BaseSuccessCode {

    LIST_SUCCESS(HttpStatus.OK, "BUCKET200_1", "가족 버킷리스트를 조회했습니다."),
    SAVE_SUCCESS(HttpStatus.CREATED, "BUCKET201_1", "버킷리스트에 저장했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
