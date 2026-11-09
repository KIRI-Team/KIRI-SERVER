package com.kiri.server.domain.suggestion.dto;

import lombok.Builder;

public class SuggestionResDTO {

    @Builder
    public record Create(
            Long suggestionId
    ) {}
}
