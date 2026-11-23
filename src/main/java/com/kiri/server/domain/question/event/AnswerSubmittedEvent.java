package com.kiri.server.domain.question.event;

/**
 * 오늘의 질문 답변이 새로 작성되거나 내용이 바뀌었을 때 발행한다.
 * 커밋 후 취향 분류(AI)를 비동기로 돌리는 데 쓴다.
 */
public record AnswerSubmittedEvent(Long memberAnswerId) {
}
