package com.kiri.server.domain.question.dto;

import lombok.Builder;

import java.util.List;

public class QuestionResDTO {

    @Builder
    public record TodayQuestion(
            Boolean hasFamily,
            Long questionId,
            String question,
            Boolean isAnswered,
            String answer
    ) {
    }

    @Builder
    public record FamilyAnswers(
            String question,
            List<FamilyAnswer> answers
    ) {
    }

    @Builder
    public record FamilyAnswer(
            Boolean isMe,
            Long memberId,
            String nickname,
            Boolean isAnswered,
            String answer
    ) {
    }
}
