package com.kiri.server.domain.term.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class TermReqDTO {


    public record Agree(
            @Valid
            List<@NotNull(message = "약관 동의 항목은 null일 수 없습니다.") Agreement> agreements
    ) {
    }

    public record Agreement(
            @NotNull(message = "termId는 필수입니다.")
            Long termId,
            @NotNull(message = "isAgreed는 필수입니다.")
            Boolean isAgreed
    ) {
    }
}
