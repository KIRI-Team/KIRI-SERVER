package com.kiri.server.domain.bucket.exception.code;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BucketErrorCode implements BaseErrorCode {

    FAMILY_REQUIRED(HttpStatus.FORBIDDEN, "BUCKET403_1", "가족공간에 소속된 회원만 이용할 수 있습니다."),
    ALREADY_SAVED(HttpStatus.CONFLICT, "BUCKET409_1", "이미 버킷리스트에 저장한 미션입니다."),
    MISSION_NOT_SAVABLE(HttpStatus.CONFLICT, "BUCKET409_2", "종료되어 저장할 수 없는 미션입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
