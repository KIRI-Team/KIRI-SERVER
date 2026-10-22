package com.kiri.server.domain.question.exception.code;

import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum QuestionSuccessCode implements BaseSuccessCode {

    TODAY_QUESTION(HttpStatus.OK, "QUESTION200_1", "오늘의 질문을 성공적으로 조회하였습니다."),
    WRITE_ANSWER(HttpStatus.OK, "QUESTION200_2", "답변을 성공적으로 작성하였습니다."),
    FAMILY_ANSWERS(HttpStatus.OK, "QUESTION200_3", "가족 답변을 성공적으로 조회하였습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
