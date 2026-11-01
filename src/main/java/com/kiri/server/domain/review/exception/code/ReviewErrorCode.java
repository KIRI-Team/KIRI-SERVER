package com.kiri.server.domain.review.exception.code;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReviewErrorCode implements BaseErrorCode {

    NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "REVIEW403_1", "해당 일정의 참여자가 아닙니다."),
    DUPLICATE_REVIEW(HttpStatus.CONFLICT, "REVIEW409_1", "이미 해당 일정에 대한 리뷰를 작성했습니다."),
    INVALID_SCHEDULE_STATE(HttpStatus.CONFLICT, "REVIEW409_2", "리뷰를 작성할 수 없는 일정 상태입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
