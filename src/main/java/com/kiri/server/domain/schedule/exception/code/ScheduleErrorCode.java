package com.kiri.server.domain.schedule.exception.code;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ScheduleErrorCode implements BaseErrorCode {

    INVALID_SCHEDULE_DATETIME(HttpStatus.BAD_REQUEST, "SCHEDULE400_1", "일정 날짜·시간을 올바르게 입력해 주세요."),
    PAST_SCHEDULE_DATETIME(HttpStatus.BAD_REQUEST, "SCHEDULE400_2", "지난 시각으로는 일정을 만들 수 없습니다."),
    OUT_OF_EVENT_PERIOD(HttpStatus.BAD_REQUEST, "SCHEDULE400_3", "행사 운영기간 안의 날짜만 선택할 수 있습니다."),
    INVALID_PARTICIPANT(HttpStatus.BAD_REQUEST, "SCHEDULE400_4", "같은 가족 구성원만 초대할 수 있습니다."),

    FAMILY_REQUIRED(HttpStatus.FORBIDDEN, "SCHEDULE403_1", "가족 공간이 없습니다."),
    BUCKET_FORBIDDEN(HttpStatus.FORBIDDEN, "SCHEDULE403_2", "같은 가족의 버킷 항목에서만 일정을 만들 수 있습니다."),
    NOT_INVITED(HttpStatus.FORBIDDEN, "SCHEDULE403_3", "초대받은 일정에만 응답할 수 있습니다."),
    NOT_PROPOSER(HttpStatus.FORBIDDEN, "SCHEDULE403_4", "일정 제안자만 수정·취소할 수 있습니다."),

    BUCKET_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE404_1", "존재하지 않는 버킷 항목입니다."),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE404_2", "존재하지 않는 일정입니다."),

    BUCKET_NOT_SCHEDULABLE(HttpStatus.CONFLICT, "SCHEDULE409_1", "일정을 만들 수 없는 버킷 항목입니다."),
    RESPONSE_CLOSED(HttpStatus.CONFLICT, "SCHEDULE409_2", "응답할 수 없는 일정입니다."),
    NOT_CANCELABLE(HttpStatus.CONFLICT, "SCHEDULE409_3", "취소할 수 없는 일정입니다."),
    NOT_UPDATABLE(HttpStatus.CONFLICT, "SCHEDULE409_4", "수정할 수 없는 일정입니다."),
    SCHEDULE_CANCELLED(HttpStatus.CONFLICT, "SCHEDULE409_7", "취소된 일정입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
