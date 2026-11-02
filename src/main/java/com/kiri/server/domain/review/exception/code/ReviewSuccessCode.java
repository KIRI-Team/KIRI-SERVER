package com.kiri.server.domain.review.exception.code;

import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReviewSuccessCode implements BaseSuccessCode {

    REVIEW_CREATED(HttpStatus.CREATED, "REVIEW200_1", "리뷰 작성이 완료되었습니다."),
    REVIEW_LIST(HttpStatus.OK, "REVIEW200_2", "리뷰 목록 조회가 완료되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
