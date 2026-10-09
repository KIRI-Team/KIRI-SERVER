package com.kiri.server.domain.term.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

public class TermResDTO {

    @Builder
    public record Info(
            Long termId,
            String title,
            String shortDescription,
            String description,
            Boolean isRequired,
            LocalDateTime updatedAt) {}

    @Builder
    public record AgreementStatus(Boolean termsAgreed, List<AgreementInfo> agreements) {}

    @Builder
    public record AgreementInfo(Long termId, Boolean isAgreed) {}
}
