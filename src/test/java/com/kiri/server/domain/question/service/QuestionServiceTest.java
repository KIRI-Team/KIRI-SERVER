package com.kiri.server.domain.question.service;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.enums.FamilyRole;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.question.dto.QuestionReqDTO;
import com.kiri.server.domain.question.dto.QuestionResDTO;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import com.kiri.server.domain.question.entity.MemberAnswer;
import com.kiri.server.domain.question.entity.Question;
import com.kiri.server.domain.question.exception.QuestionException;
import com.kiri.server.domain.question.exception.code.QuestionErrorCode;
import com.kiri.server.domain.question.repository.FamilyQuestionRepository;
import com.kiri.server.domain.question.repository.MemberAnswerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
    private static final QuestionReqDTO.WriteAnswer WRITE_REQUEST =
            new QuestionReqDTO.WriteAnswer("강아지가 산책하다 넘어졌어요.");

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private FamilyQuestionRepository familyQuestionRepository;

    @Mock
    private MemberAnswerRepository memberAnswerRepository;

    private QuestionService questionService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-10T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final Family family = Family.builder().id(1L).name("행복한 우리집").build();
    private final Member member = Member.builder().id(MEMBER_ID).family(family).build();
    private final Question question = Question.builder().id(100L).content("오늘 가장 웃겼던 일은?").build();
    private final LocalDate today = LocalDate.now(clock);
    private final FamilyQuestion familyQuestion = FamilyQuestion.builder()
            .id(FAMILY_QUESTION_ID)
            .family(family)
            .question(question)
            .questionDate(today)
            .build();

    @BeforeEach
    void setUp() {
        questionService = new QuestionService(
                memberRepository, familyQuestionRepository, memberAnswerRepository, clock);
    }

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

    @Test
    @DisplayName("답변 작성 시 존재하지 않는 회원이면 MEMBER404_1 예외가 발생한다.")
    void writeAnswer_memberNotFound() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> questionService.writeAnswer(MEMBER_ID, FAMILY_QUESTION_ID, WRITE_REQUEST))
                .isInstanceOf(MemberException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("답변 작성 시 질문이 존재하지 않으면 QUESTION404_2 예외가 발생한다.")
    void writeAnswer_familyQuestionNotFound() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(familyQuestionRepository.findById(FAMILY_QUESTION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> questionService.writeAnswer(MEMBER_ID, FAMILY_QUESTION_ID, WRITE_REQUEST))
                .isInstanceOf(QuestionException.class)
                .extracting("errorCode")
                .isEqualTo(QuestionErrorCode.FAMILY_QUESTION_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 가족의 질문에 답변하면 QUESTION404_2 예외가 발생하고 저장하지 않는다.")
    void writeAnswer_otherFamilyQuestion() {
        Family otherFamily = Family.builder().id(2L).name("옆집").build();
        FamilyQuestion otherFamilyQuestion = FamilyQuestion.builder()
                .id(FAMILY_QUESTION_ID)
                .family(otherFamily)
                .question(question)
                .questionDate(LocalDate.of(2026, 10, 10))
                .build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(familyQuestionRepository.findById(FAMILY_QUESTION_ID)).willReturn(Optional.of(otherFamilyQuestion));

        assertThatThrownBy(() -> questionService.writeAnswer(MEMBER_ID, FAMILY_QUESTION_ID, WRITE_REQUEST))
                .isInstanceOf(QuestionException.class)
                .extracting("errorCode")
                .isEqualTo(QuestionErrorCode.FAMILY_QUESTION_NOT_FOUND);
        verify(memberAnswerRepository, never()).save(any());
    }

    @Test
    @DisplayName("가족이 없는 회원이 답변하면 QUESTION404_2 예외가 발생한다.")
    void writeAnswer_noFamily() {
        Member memberWithoutFamily = Member.builder().id(MEMBER_ID).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(memberWithoutFamily));
        given(familyQuestionRepository.findById(FAMILY_QUESTION_ID)).willReturn(Optional.of(familyQuestion));

        assertThatThrownBy(() -> questionService.writeAnswer(MEMBER_ID, FAMILY_QUESTION_ID, WRITE_REQUEST))
                .isInstanceOf(QuestionException.class)
                .extracting("errorCode")
                .isEqualTo(QuestionErrorCode.FAMILY_QUESTION_NOT_FOUND);
    }

    @Test
    @DisplayName("지난 날짜의 질문에 답변하면 QUESTION400_1 예외가 발생하고 저장하지 않는다.")
    void writeAnswer_periodExpired() {
        FamilyQuestion yesterdayQuestion = FamilyQuestion.builder()
                .id(FAMILY_QUESTION_ID)
                .family(family)
                .question(question)
                .questionDate(today.minusDays(1))
                .build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(familyQuestionRepository.findById(FAMILY_QUESTION_ID)).willReturn(Optional.of(yesterdayQuestion));

        assertThatThrownBy(() -> questionService.writeAnswer(MEMBER_ID, FAMILY_QUESTION_ID, WRITE_REQUEST))
                .isInstanceOf(QuestionException.class)
                .extracting("errorCode")
                .isEqualTo(QuestionErrorCode.ANSWER_PERIOD_EXPIRED);
        verify(memberAnswerRepository, never()).findByFamilyQuestionAndMember(any(), any());
    }

    @Test
    @DisplayName("답변이 없으면 요청 내용으로 새 답변을 저장한다.")
    void writeAnswer_create() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(familyQuestionRepository.findById(FAMILY_QUESTION_ID)).willReturn(Optional.of(familyQuestion));
        given(memberAnswerRepository.findByFamilyQuestionAndMember(familyQuestion, member))
                .willReturn(Optional.empty());

        questionService.writeAnswer(MEMBER_ID, FAMILY_QUESTION_ID, WRITE_REQUEST);

        // save에 넘어간 엔티티를 붙잡아 값이 제대로 채워졌는지 확인
        ArgumentCaptor<MemberAnswer> captor = ArgumentCaptor.forClass(MemberAnswer.class);
        verify(memberAnswerRepository).save(captor.capture());
        MemberAnswer saved = captor.getValue();
        assertThat(saved.getContent()).isEqualTo(WRITE_REQUEST.answer());
        assertThat(saved.getFamilyQuestion()).isEqualTo(familyQuestion);
        assertThat(saved.getMember()).isEqualTo(member);
    }

    @Test
    @DisplayName("답변이 이미 있으면 새로 저장하지 않고 기존 답변 내용을 덮어쓴다.")
    void writeAnswer_update() {
        MemberAnswer existing = MemberAnswer.builder()
                .content("이전 답변")
                .familyQuestion(familyQuestion)
                .member(member)
                .build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(familyQuestionRepository.findById(FAMILY_QUESTION_ID)).willReturn(Optional.of(familyQuestion));
        given(memberAnswerRepository.findByFamilyQuestionAndMember(familyQuestion, member))
                .willReturn(Optional.of(existing));

        questionService.writeAnswer(MEMBER_ID, FAMILY_QUESTION_ID, WRITE_REQUEST);

        assertThat(existing.getContent()).isEqualTo(WRITE_REQUEST.answer());
        verify(memberAnswerRepository, never()).save(any());
    }

    @Test
    @DisplayName("첫 답변 작성이 동시에 들어와 답변 유니크 제약에 막히면 QUESTION409_1 예외가 발생한다.")
    void writeAnswer_duplicateRequest() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(familyQuestionRepository.findById(FAMILY_QUESTION_ID)).willReturn(Optional.of(familyQuestion));
        given(memberAnswerRepository.findByFamilyQuestionAndMember(familyQuestion, member))
                .willReturn(Optional.empty());
        // 조회 시점엔 없었지만 다른 요청이 먼저 저장한 상황
        given(memberAnswerRepository.save(any(MemberAnswer.class))).willThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new PSQLException(new ServerErrorMessage(
                        "SERROR\0C23505\0Mduplicate key value violates unique constraint\0nuk_member_answer\0"))));

        assertThatThrownBy(() -> questionService.writeAnswer(MEMBER_ID, FAMILY_QUESTION_ID, WRITE_REQUEST))
                .isInstanceOf(QuestionException.class)
                .extracting("errorCode")
                .isEqualTo(QuestionErrorCode.DUPLICATE_ANSWER_REQUEST);
    }

    @Test
    @DisplayName("답변 유니크 제약이 아닌 무결성 위반은 바꾸지 않고 그대로 던진다.")
    void writeAnswer_otherIntegrityViolation() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(familyQuestionRepository.findById(FAMILY_QUESTION_ID)).willReturn(Optional.of(familyQuestion));
        given(memberAnswerRepository.findByFamilyQuestionAndMember(familyQuestion, member))
                .willReturn(Optional.empty());
        // 회원이 그 사이 탈퇴해 FK 위반이 난 상황
        given(memberAnswerRepository.save(any(MemberAnswer.class))).willThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new PSQLException(new ServerErrorMessage(
                        "SERROR\0C23503\0Minsert or update violates foreign key constraint\0nfk_member_answer_member\0"))));

        assertThatThrownBy(() -> questionService.writeAnswer(MEMBER_ID, FAMILY_QUESTION_ID, WRITE_REQUEST))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("가족 답변 조회 시 질문이 존재하지 않으면 QUESTION404_2 예외가 발생한다.")
    void getFamilyAnswers_familyQuestionNotFound() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(familyQuestionRepository.findById(FAMILY_QUESTION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> questionService.getFamilyAnswers(MEMBER_ID, FAMILY_QUESTION_ID))
                .isInstanceOf(QuestionException.class)
                .extracting("errorCode")
                .isEqualTo(QuestionErrorCode.FAMILY_QUESTION_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 가족의 질문으로 가족 답변을 조회하면 QUESTION404_2 예외가 발생하고 가족을 조회하지 않는다.")
    void getFamilyAnswers_otherFamilyQuestion() {
        Family otherFamily = Family.builder().id(2L).name("옆집").build();
        FamilyQuestion otherFamilyQuestion = FamilyQuestion.builder()
                .id(FAMILY_QUESTION_ID)
                .family(otherFamily)
                .question(question)
                .questionDate(today)
                .build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(familyQuestionRepository.findById(FAMILY_QUESTION_ID)).willReturn(Optional.of(otherFamilyQuestion));

        assertThatThrownBy(() -> questionService.getFamilyAnswers(MEMBER_ID, FAMILY_QUESTION_ID))
                .isInstanceOf(QuestionException.class)
                .extracting("errorCode")
                .isEqualTo(QuestionErrorCode.FAMILY_QUESTION_NOT_FOUND);
        verify(memberRepository, never()).findAllByFamily(any());
    }

    @Test
    @DisplayName("아직 공개되지 않은 미래 질문으로 가족 답변을 조회하면 QUESTION404_2 예외가 발생한다.")
    void getFamilyAnswers_futureQuestion() {
        FamilyQuestion tomorrowQuestion = FamilyQuestion.builder()
                .id(FAMILY_QUESTION_ID)
                .family(family)
                .question(question)
                .questionDate(today.plusDays(1))
                .build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(familyQuestionRepository.findById(FAMILY_QUESTION_ID)).willReturn(Optional.of(tomorrowQuestion));

        assertThatThrownBy(() -> questionService.getFamilyAnswers(MEMBER_ID, FAMILY_QUESTION_ID))
                .isInstanceOf(QuestionException.class)
                .extracting("errorCode")
                .isEqualTo(QuestionErrorCode.FAMILY_QUESTION_NOT_FOUND);
        verify(memberRepository, never()).findAllByFamily(any());
    }

    @Test
    @DisplayName("가족 답변 조회 시 나를 맨 앞에 두고 나머지는 가족 역할 순서로 정렬하며, 답변하지 않은 구성원도 포함한다.")
    void getFamilyAnswers_success() {
        Long myId = 1000L;
        Member me = Member.builder().id(myId).nickname("끼리").familyRole(FamilyRole.SON).family(family).build();
        Member mother = Member.builder().id(1001L).nickname("엄마").familyRole(FamilyRole.MOTHER).family(family).build();
        Member father = Member.builder().id(1002L).nickname("아빠").familyRole(FamilyRole.FATHER).family(family).build();
        Member daughter = Member.builder().id(1003L).nickname("딸").familyRole(FamilyRole.DAUGHTER).family(family).build();
        MemberAnswer myAnswer = MemberAnswer.builder()
                .content("강아지가 산책하다 넘어졌어요.")
                .familyQuestion(familyQuestion)
                .member(me)
                .build();
        MemberAnswer motherAnswer = MemberAnswer.builder()
                .content("뚝배기불고기")
                .familyQuestion(familyQuestion)
                .member(mother)
                .build();
        given(memberRepository.findById(myId)).willReturn(Optional.of(me));
        given(familyQuestionRepository.findById(FAMILY_QUESTION_ID)).willReturn(Optional.of(familyQuestion));
        given(memberRepository.findAllByFamily(family)).willReturn(List.of(daughter, father, me, mother));
        given(memberAnswerRepository.findAllByFamilyQuestion(familyQuestion)).willReturn(List.of(myAnswer, motherAnswer));

        QuestionResDTO.FamilyAnswers result = questionService.getFamilyAnswers(myId, FAMILY_QUESTION_ID);

        // 나(아들) → 엄마 → 아빠 → 딸
        assertThat(result.question()).isEqualTo("오늘 가장 웃겼던 일은?");
        assertThat(result.answers())
                .extracting(QuestionResDTO.FamilyAnswer::memberId)
                .containsExactly(myId, 1001L, 1002L, 1003L);
        assertThat(result.answers())
                .extracting(QuestionResDTO.FamilyAnswer::isMe)
                .containsExactly(true, false, false, false);

        QuestionResDTO.FamilyAnswer fatherResult = result.answers().get(2);
        assertThat(fatherResult.isAnswered()).isFalse();
        assertThat(fatherResult.answer()).isNull();

        QuestionResDTO.FamilyAnswer motherResult = result.answers().get(1);
        assertThat(motherResult.nickname()).isEqualTo("엄마");
        assertThat(motherResult.isAnswered()).isTrue();
        assertThat(motherResult.answer()).isEqualTo("뚝배기불고기");
    }

    @Test
    @DisplayName("지난 날짜의 질문도 가족 답변을 조회할 수 있다.")
    void getFamilyAnswers_pastQuestion() {
        FamilyQuestion yesterdayQuestion = FamilyQuestion.builder()
                .id(FAMILY_QUESTION_ID)
                .family(family)
                .question(question)
                .questionDate(today.minusDays(1))
                .build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(familyQuestionRepository.findById(FAMILY_QUESTION_ID)).willReturn(Optional.of(yesterdayQuestion));
        given(memberRepository.findAllByFamily(family)).willReturn(List.of(member));
        given(memberAnswerRepository.findAllByFamilyQuestion(yesterdayQuestion)).willReturn(List.of());

        QuestionResDTO.FamilyAnswers result = questionService.getFamilyAnswers(MEMBER_ID, FAMILY_QUESTION_ID);

        assertThat(result.answers()).hasSize(1);
        assertThat(result.answers().get(0).isAnswered()).isFalse();
    }
}
