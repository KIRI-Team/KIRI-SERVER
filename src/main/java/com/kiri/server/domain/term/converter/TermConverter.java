package com.kiri.server.domain.term.converter;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.term.dto.TermResDTO;
import com.kiri.server.domain.term.entity.MemberTerm;
import com.kiri.server.domain.term.entity.Term;
import java.time.LocalDateTime;
import java.util.List;

public class TermConverter {

    public static TermResDTO.Info toInfo(Term term) {
        LocalDateTime lastModifiedAt = term.getUpdatedAt() != null ? term.getUpdatedAt() : term.getCreatedAt();

        return TermResDTO.Info.builder()
                .termId(term.getId())
                .title(term.getTitle())
                .shortDescription(term.getShortDescription())
                .description(term.getDescription())
                .isRequired(term.getIsRequired())
                .updatedAt(lastModifiedAt)
                .build();
    }

    // 약관별 동의 내역
    public static TermResDTO.AgreementInfo toAgreementInfo(Long termId, boolean isAgreed) {
        return TermResDTO.AgreementInfo.builder()
                .termId(termId)
                .isAgreed(isAgreed)
                .build();
    }

    public static TermResDTO.AgreementStatus toAgreementStatus(
            boolean termsAgreed, List<TermResDTO.AgreementInfo> agreements) {
        return TermResDTO.AgreementStatus.builder()
                .termsAgreed(termsAgreed)
                .agreements(agreements)
                .build();
    }

    public static MemberTerm toMemberTerm(Member member, Term term, boolean isAgreed) {
        return MemberTerm.builder()
                .member(member)
                .term(term)
                .termVersion(term.getVersion())
                .agreedAt(isAgreed ? LocalDateTime.now() : null)
                .build();
    }
}
