package com.kiri.server.domain.review.exception.code;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReviewErrorCode implements BaseErrorCode {

    NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "REVIEW403_1", "해당 일정의 참여자가 아닙니다."),
    FORBIDDEN_FAMILY(HttpStatus.FORBIDDEN, "REVIEW403_2", "해당 일정의 리뷰를 조회할 권한이 없습니다."),
    NOT_REVIEW_WRITER(HttpStatus.FORBIDDEN, "REVIEW403_3", "본인이 작성한 리뷰만 수정·삭제할 수 있습니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW404_1", "존재하지 않는 리뷰입니다."),
    REVIEW_PHOTO_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW404_2", "해당 일정에 존재하지 않는 리뷰 사진입니다."),
    DUPLICATE_REVIEW(HttpStatus.CONFLICT, "REVIEW409_1", "이미 해당 일정에 대한 리뷰를 작성했습니다."),
    INVALID_SCHEDULE_STATE(HttpStatus.CONFLICT, "REVIEW409_2", "리뷰를 작성할 수 없는 일정 상태입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
