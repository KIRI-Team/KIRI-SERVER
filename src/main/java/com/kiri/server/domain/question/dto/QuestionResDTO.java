package com.kiri.server.domain.question.dto;

import lombok.Builder;

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
}
