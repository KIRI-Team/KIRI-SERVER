package com.kiri.server.domain.schedule.exception.code;

import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ScheduleSuccessCode implements BaseSuccessCode {

    CALENDAR_SUCCESS(HttpStatus.OK, "SCHEDULE200_1", "캘린더 월간 일정 조회 성공"),
    RESPONSE_SUCCESS(HttpStatus.OK, "SCHEDULE200_2", "일정 응답 처리 성공"),
    CANCEL_SUCCESS(HttpStatus.OK, "SCHEDULE200_3", "일정 취소 성공"),
    UPDATE_SUCCESS(HttpStatus.OK, "SCHEDULE200_4", "일정 수정 성공"),
    UPCOMING_SUCCESS(HttpStatus.OK, "SCHEDULE200_5", "다가오는 일정 조회 성공"),
    CREATE_SUCCESS(HttpStatus.CREATED, "SCHEDULE201_1", "일정 생성 성공");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
