package com.kiri.server.domain.suggestion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SuggestionReqDTO {

    public record Create(
            @NotBlank(message = "건의 내용은 필수입니다.")
            @Size(max = 500, message = "건의 내용은 최대 500자까지 입력할 수 있습니다.")
            String content,
            String imageKey
    ) {}
}
