package com.kiri.server.domain.review.exception.code;

import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReviewSuccessCode implements BaseSuccessCode {

    REVIEW_CREATED(HttpStatus.CREATED, "REVIEW200_1", "리뷰 작성이 완료되었습니다."),
    REVIEW_LIST(HttpStatus.OK, "REVIEW200_2", "리뷰 목록 조회가 완료되었습니다."),
    REVIEW_UPDATED(HttpStatus.OK, "REVIEW200_3", "리뷰 수정이 완료되었습니다."),
    REVIEW_DELETED(HttpStatus.OK, "REVIEW200_4", "리뷰 삭제가 완료되었습니다."),
    REPRESENTATIVE_PHOTO_CHANGED(HttpStatus.OK, "REVIEW200_5", "대표사진 변경이 완료되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
