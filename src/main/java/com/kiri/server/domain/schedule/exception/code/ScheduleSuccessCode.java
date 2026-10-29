package com.kiri.server.domain.schedule.exception.code;

import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ScheduleSuccessCode implements BaseSuccessCode {

    CALENDAR_SUCCESS(HttpStatus.OK, "SCHEDULE200_1", "캘린더 월간 일정 조회 성공");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
