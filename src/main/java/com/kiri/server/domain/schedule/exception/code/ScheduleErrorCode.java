package com.kiri.server.domain.schedule.exception.code;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ScheduleErrorCode implements BaseErrorCode {

    FAMILY_REQUIRED(HttpStatus.FORBIDDEN, "SCHEDULE403_1", "가족 공간이 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
