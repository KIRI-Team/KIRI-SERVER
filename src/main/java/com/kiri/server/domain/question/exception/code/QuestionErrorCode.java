package com.kiri.server.domain.question.exception.code;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum QuestionErrorCode implements BaseErrorCode {

    ANSWER_PERIOD_EXPIRED(HttpStatus.BAD_REQUEST, "QUESTION400_1", "오늘의 질문에만 답변할 수 있습니다."),
    SELF_POKE(HttpStatus.BAD_REQUEST, "QUESTION400_2", "자기 자신은 찌를 수 없습니다."),
    FAMILY_REQUIRED(HttpStatus.FORBIDDEN, "QUESTION403_1", "가족에 소속된 회원만 이용할 수 있습니다."),
    TODAY_QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "QUESTION404_1", "오늘의 질문이 존재하지 않습니다."),
    // 다른 가족의 질문도 존재 여부를 숨기기 위해 같은 404로 응답
    FAMILY_QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "QUESTION404_2", "질문이 존재하지 않습니다."),
    // 없는 회원과 다른 가족 회원을 구분하지 않고 같은 404로 응답
    POKE_RECEIVER_NOT_FOUND(HttpStatus.NOT_FOUND, "QUESTION404_3", "찌를 수 있는 가족 구성원이 아닙니다."),
    // 같은 회원의 첫 답변 작성 요청이 동시에 들어와 uk_member_answer에 막힌 경우
    DUPLICATE_ANSWER_REQUEST(HttpStatus.CONFLICT, "QUESTION409_1", "답변 요청이 동시에 처리되었습니다. 다시 시도해주세요."),
    ALREADY_POKED(HttpStatus.CONFLICT, "QUESTION409_2", "오늘은 이미 찌른 가족 구성원입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
