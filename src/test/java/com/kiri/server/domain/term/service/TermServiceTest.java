package com.kiri.server.domain.term.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TermServiceTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private TermRepository termRepository;

    @Mock
    private MemberTermRepository memberTermRepository;

    @InjectMocks
    private TermService termService;

    private final Member member = Member.builder().id(MEMBER_ID).build();
    private final Term requiredTerm = createTerm(1L, true, 1);
    private final Term optionalTerm = createTerm(2L, false, 1);

    @Test
    @DisplayName("존재하지 않는 회원이면 MEMBER404_1 예외가 발생한다.")
    void agree_memberNotFound() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> termService.agree(MEMBER_ID, request(agreement(1L, true))))
                .isInstanceOf(MemberException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 약관 ID가 포함되면 TERM404_1 예외가 발생한다.")
    void agree_termNotFound() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(termRepository.findAllByOrderByIdAsc()).willReturn(List.of(requiredTerm, optionalTerm));

        assertThatThrownBy(() -> termService.agree(MEMBER_ID, request(agreement(1L, true), agreement(99L, true))))
                .isInstanceOf(TermException.class)
                .extracting("errorCode")
                .isEqualTo(TermErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("필수 약관에 동의하지 않으면 TERM400_1 예외가 발생한다.")
    void agree_requiredNotAgreed() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(termRepository.findAllByOrderByIdAsc()).willReturn(List.of(requiredTerm, optionalTerm));

        assertThatThrownBy(() -> termService.agree(MEMBER_ID, request(agreement(1L, false), agreement(2L, true))))
                .isInstanceOf(TermException.class)
                .extracting("errorCode")
                .isEqualTo(TermErrorCode.REQUIRED_NOT_AGREED);
    }

    @Test
    @DisplayName("빈 배열로 요청하면 필수 약관 미동의로 TERM400_1 예외가 발생한다.")
    void agree_emptyAgreements() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(termRepository.findAllByOrderByIdAsc()).willReturn(List.of(requiredTerm, optionalTerm));

        assertThatThrownBy(() -> termService.agree(MEMBER_ID, new TermReqDTO.Agree(List.of())))
                .isInstanceOf(TermException.class)
                .extracting("errorCode")
                .isEqualTo(TermErrorCode.REQUIRED_NOT_AGREED);
    }

    @Test
    @DisplayName("동의 기록이 없으면 약관별로 새 동의 기록을 저장한다.")
    void agree_saveNewAgreements() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(termRepository.findAllByOrderByIdAsc()).willReturn(List.of(requiredTerm, optionalTerm));
        given(memberTermRepository.findAllByMember(member)).willReturn(List.of());

        termService.agree(MEMBER_ID, request(agreement(1L, true), agreement(2L, false)));

        ArgumentCaptor<MemberTerm> captor = ArgumentCaptor.forClass(MemberTerm.class);
        verify(memberTermRepository, times(2)).save(captor.capture());

        MemberTerm saved = captor.getAllValues().stream()
                .filter(memberTerm -> memberTerm.getTerm().getId().equals(1L))
                .findFirst()
                .orElseThrow();
        MemberTerm notAgreed = captor.getAllValues().stream()
                .filter(memberTerm -> memberTerm.getTerm().getId().equals(2L))
                .findFirst()
                .orElseThrow();

        assertThat(saved.getAgreedAt()).isNotNull();
        assertThat(saved.getTermVersion()).isEqualTo(1);
        assertThat(notAgreed.getAgreedAt()).isNull();
    }

    @Test
    @DisplayName("동의 기록이 이미 있으면 새로 저장하지 않고 기존 기록을 갱신한다.")
    void agree_updateExistingAgreement() {
        MemberTerm existing = TermConverter.toMemberTerm(member, requiredTerm, true);
        LocalDateTime firstAgreedAt = existing.getAgreedAt();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(termRepository.findAllByOrderByIdAsc()).willReturn(List.of(requiredTerm));
        given(memberTermRepository.findAllByMember(member)).willReturn(List.of(existing));

        termService.agree(MEMBER_ID, request(agreement(1L, true)));

        verify(memberTermRepository, never()).save(any());
        assertThat(existing.getAgreedAt()).isEqualTo(firstAgreedAt);
    }

    @Test
    @DisplayName("동의 여부 조회 시 이전 버전에 동의한 약관은 미동의로 반환한다.")
    void getAgreementStatus_oldVersionIsNotAgreed() {
        Term updatedTerm = createTerm(1L, true, 2);
        MemberTerm oldAgreement = TermConverter.toMemberTerm(member, requiredTerm, true);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(memberTermRepository.areAllRequiredTermsAgreed(MEMBER_ID)).willReturn(false);
        given(memberTermRepository.findAllByMember(member)).willReturn(List.of(oldAgreement));
        given(termRepository.findAllByOrderByIdAsc()).willReturn(List.of(updatedTerm, optionalTerm));

        TermResDTO.AgreementStatus status = termService.getAgreementStatus(MEMBER_ID);

        assertThat(status.termsAgreed()).isFalse();
        assertThat(status.agreements())
                .extracting(TermResDTO.AgreementInfo::termId, TermResDTO.AgreementInfo::isAgreed)
                .containsExactly(tuple(1L, false), tuple(2L, false));
    }

    @Test
    @DisplayName("존재하지 않는 회원의 동의 여부를 조회하면 MEMBER404_1 예외가 발생한다.")
    void getAgreementStatus_memberNotFound() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> termService.getAgreementStatus(MEMBER_ID))
                .isInstanceOf(MemberException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.NOT_FOUND);
    }

    private Term createTerm(Long id, boolean isRequired, int version) {
        return Term.builder()
                .id(id)
                .title("약관 " + id)
                .shortDescription("약관 요약 " + id)
                .description("약관 본문 " + id)
                .isRequired(isRequired)
                .version(version)
                .build();
    }

    private TermReqDTO.Agreement agreement(Long termId, boolean isAgreed) {
        return new TermReqDTO.Agreement(termId, isAgreed);
    }

    private TermReqDTO.Agree request(TermReqDTO.Agreement... agreements) {
        return new TermReqDTO.Agree(List.of(agreements));
    }
}
