package com.kiri.server.domain.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class QuestionReqDTO {
    public record WriteAnswer(
            @NotBlank(message = "답변을 입력해주세요.")
            @Size(max = 500, message = "답변은 500자 이내로 작성해주세요.")
            String answer
    ) {
    }
}
