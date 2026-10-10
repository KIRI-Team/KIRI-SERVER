package com.kiri.server.domain.question.service;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.question.dto.QuestionResDTO;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import com.kiri.server.domain.question.entity.MemberAnswer;
import com.kiri.server.domain.question.entity.Question;
import com.kiri.server.domain.question.exception.QuestionException;
import com.kiri.server.domain.question.exception.code.QuestionErrorCode;
import com.kiri.server.domain.question.repository.FamilyQuestionRepository;
import com.kiri.server.domain.question.repository.MemberAnswerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long FAMILY_QUESTION_ID = 10L;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private FamilyQuestionRepository familyQuestionRepository;

    @Mock
    private MemberAnswerRepository memberAnswerRepository;

    @InjectMocks
    private QuestionService questionService;

    private final Family family = Family.builder().id(1L).name("행복한 우리집").build();
    private final Member member = Member.builder().id(MEMBER_ID).family(family).build();
    private final Question question = Question.builder().id(100L).content("오늘 가장 웃겼던 일은?").build();
    private final FamilyQuestion familyQuestion = FamilyQuestion.builder()
            .id(FAMILY_QUESTION_ID)
            .family(family)
            .question(question)
            .questionDate(LocalDate.of(2026, 10, 10))
            .build();

    @Test
    @DisplayName("존재하지 않는 회원이면 MEMBER404_1 예외가 발생한다.")
    void getTodayQuestion_memberNotFound() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> questionService.getTodayQuestion(MEMBER_ID))
                .isInstanceOf(MemberException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("가족이 없는 회원이면 예외 없이 hasFamily가 false인 응답을 반환하고 질문을 조회하지 않는다.")
    void getTodayQuestion_noFamily() {
        Member memberWithoutFamily = Member.builder().id(MEMBER_ID).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(memberWithoutFamily));

        QuestionResDTO.TodayQuestion result = questionService.getTodayQuestion(MEMBER_ID);

        assertThat(result.hasFamily()).isFalse();
        assertThat(result.questionId()).isNull();
        assertThat(result.question()).isNull();
        assertThat(result.isAnswered()).isFalse();
        assertThat(result.answer()).isNull();
        verify(familyQuestionRepository, never()).findByFamilyAndQuestionDate(any(), any());
    }

    @Test
    @DisplayName("가족에게 오늘 배정된 질문이 없으면 QUESTION404_1 예외가 발생한다.")
    void getTodayQuestion_todayQuestionNotFound() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(familyQuestionRepository.findByFamilyAndQuestionDate(eq(family), any(LocalDate.class)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> questionService.getTodayQuestion(MEMBER_ID))
                .isInstanceOf(QuestionException.class)
                .extracting("errorCode")
                .isEqualTo(QuestionErrorCode.TODAY_QUESTION_NOT_FOUND);
    }

    @Test
    @DisplayName("오늘 질문에 답변하지 않았으면 isAnswered가 false이고 answer가 null이다.")
    void getTodayQuestion_notAnswered() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(familyQuestionRepository.findByFamilyAndQuestionDate(eq(family), any(LocalDate.class)))
                .willReturn(Optional.of(familyQuestion));
        given(memberAnswerRepository.findByFamilyQuestionAndMember(familyQuestion, member))
                .willReturn(Optional.empty());

        QuestionResDTO.TodayQuestion result = questionService.getTodayQuestion(MEMBER_ID);

        assertThat(result.hasFamily()).isTrue();
        assertThat(result.questionId()).isEqualTo(FAMILY_QUESTION_ID);
        assertThat(result.question()).isEqualTo("오늘 가장 웃겼던 일은?");
        assertThat(result.isAnswered()).isFalse();
        assertThat(result.answer()).isNull();
    }

    @Test
    @DisplayName("오늘 질문에 답변했으면 isAnswered가 true이고 내 답변 내용을 반환한다.")
    void getTodayQuestion_answered() {
        MemberAnswer answer = MemberAnswer.builder()
                .content("강아지가 산책하다 넘어졌어요.")
                .familyQuestion(familyQuestion)
                .member(member)
                .build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(familyQuestionRepository.findByFamilyAndQuestionDate(eq(family), any(LocalDate.class)))
                .willReturn(Optional.of(familyQuestion));
        given(memberAnswerRepository.findByFamilyQuestionAndMember(familyQuestion, member))
                .willReturn(Optional.of(answer));

        QuestionResDTO.TodayQuestion result = questionService.getTodayQuestion(MEMBER_ID);

        assertThat(result.isAnswered()).isTrue();
        assertThat(result.answer()).isEqualTo("강아지가 산책하다 넘어졌어요.");
    }
}
