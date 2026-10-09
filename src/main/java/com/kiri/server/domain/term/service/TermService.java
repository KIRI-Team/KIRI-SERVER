package com.kiri.server.domain.term.service;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.term.converter.TermConverter;
import com.kiri.server.domain.term.dto.TermReqDTO;
import com.kiri.server.domain.term.dto.TermResDTO;
import com.kiri.server.domain.term.entity.MemberTerm;
import com.kiri.server.domain.term.entity.Term;
import com.kiri.server.domain.term.exception.TermException;
import com.kiri.server.domain.term.exception.code.TermErrorCode;
import com.kiri.server.domain.term.repository.MemberTermRepository;
import com.kiri.server.domain.term.repository.TermRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TermService {

    private final MemberRepository memberRepository;
    private final TermRepository termRepository;
    private final MemberTermRepository memberTermRepository;

    @Transactional(readOnly = true)
    public List<TermResDTO.Info> getTerms() {

        return termRepository.findAllByOrderByIdAsc().stream()
                .map(TermConverter::toInfo)
                .toList();
    }

    @Transactional
    public void agree(Long memberId, TermReqDTO.Agree request) {

        Member member =
                memberRepository.findById(memberId).orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));

        List<TermReqDTO.Agreement> agreements =
                (request == null || request.agreements() == null) ? List.of() : request.agreements();

        Map<Long, Boolean> requested = new HashMap<>();
        for (TermReqDTO.Agreement agreement : agreements) {
            requested.put(agreement.termId(), Boolean.TRUE.equals(agreement.isAgreed()));
        }

        List<Term> terms = termRepository.findAllByOrderByIdAsc();
        Map<Long, Term> termMap = terms.stream().collect(Collectors.toMap(Term::getId, term -> term));

        validateTermsExist(requested.keySet(), termMap);
        validateRequiredAgreed(terms, requested);

        saveAgreements(member, requested, termMap);
    }

    private void validateTermsExist(Set<Long> requestedTermIds, Map<Long, Term> termMap) {

        for (Long termId : requestedTermIds) {
            if (termId == null || !termMap.containsKey(termId)) {
                throw new TermException(TermErrorCode.NOT_FOUND);
            }
        }
    }

    private void validateRequiredAgreed(List<Term> terms, Map<Long, Boolean> requested) {

        for (Term term : terms) {
            if (Boolean.TRUE.equals(term.getIsRequired()) && !Boolean.TRUE.equals(requested.get(term.getId()))) {
                throw new TermException(TermErrorCode.REQUIRED_NOT_AGREED);
            }
        }
    }

    private void saveAgreements(Member member, Map<Long, Boolean> requested, Map<Long, Term> termMap) {

        Map<Long, MemberTerm> existing = memberTermRepository.findAllByMember(member).stream()
                .collect(Collectors.toMap(
                        memberTerm -> memberTerm.getTerm().getId(),
                        memberTerm -> memberTerm,
                        (first, second) -> first));

        for (Map.Entry<Long, Boolean> entry : requested.entrySet()) {
            MemberTerm memberTerm = existing.get(entry.getKey());

            if (memberTerm != null) {
                memberTerm.updateAgreement(entry.getValue(), termMap.get(entry.getKey()));
            } else {
                memberTermRepository.save(
                        TermConverter.toMemberTerm(member, termMap.get(entry.getKey()), entry.getValue()));
            }
        }
    }

    @Transactional(readOnly = true)
    public TermResDTO.AgreementStatus getAgreementStatus(Long memberId) {

        Member member =
                memberRepository.findById(memberId).orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));

        boolean termsAgreed = memberTermRepository.areAllRequiredTermsAgreed(memberId);

        Map<Long, MemberTerm> memberTermMap = memberTermRepository.findAllByMember(member).stream()
                .collect(Collectors.toMap(
                        memberTerm -> memberTerm.getTerm().getId(),
                        memberTerm -> memberTerm,
                        (first, second) -> first));

        List<TermResDTO.AgreementInfo> agreements = termRepository.findAllByOrderByIdAsc().stream()
                .map(term -> {
                    MemberTerm memberTerm = memberTermMap.get(term.getId());
                    boolean isAgreed = memberTerm != null && memberTerm.isAgreedTo(term);
                    return TermConverter.toAgreementInfo(term.getId(), isAgreed);
                })
                .toList();

        return TermConverter.toAgreementStatus(termsAgreed, agreements);
    }
}
