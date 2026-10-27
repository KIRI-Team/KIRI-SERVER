package com.kiri.server.domain.question.service;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.family.repository.FamilyRepository;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.enums.FamilyRole;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.question.dto.QuestionReqDTO;
import com.kiri.server.domain.question.dto.QuestionResDTO;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import com.kiri.server.domain.question.entity.MemberAnswer;
import com.kiri.server.domain.question.entity.Poke;
import com.kiri.server.domain.question.entity.Question;
import com.kiri.server.domain.question.enums.TasteType;
import com.kiri.server.domain.question.event.PokeEvent;
import com.kiri.server.domain.question.exception.QuestionException;
import com.kiri.server.domain.question.exception.code.QuestionErrorCode;
import com.kiri.server.domain.question.repository.FamilyQuestionRepository;
import com.kiri.server.domain.question.repository.FamilyQuestionRepository.TasteTypeCount;
import com.kiri.server.domain.question.repository.MemberAnswerRepository;
import com.kiri.server.domain.question.repository.PokeRepository;
import com.kiri.server.domain.question.repository.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

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
    private static final Long RECEIVER_ID = 2L;
    private static final QuestionReqDTO.WriteAnswer WRITE_REQUEST =
            new QuestionReqDTO.WriteAnswer("강아지가 산책하다 넘어졌어요.");

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private FamilyQuestionRepository familyQuestionRepository;

    @Mock
    private MemberAnswerRepository memberAnswerRepository;

    @Mock
    private PokeRepository pokeRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private FamilyRepository familyRepository;

    @Mock
    private QuestionRepository questionRepository;

    private QuestionService questionService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-10T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final Family family = Family.builder().id(1L).name("행복한 우리집").build();
    private final Member member = Member.builder().id(MEMBER_ID).family(family).build();
    private final Member receiver = Member.builder().id(RECEIVER_ID).family(family).build();
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
                memberRepository, familyQuestionRepository, memberAnswerRepository, clock, pokeRepository, eventPublisher,
                familyRepository, questionRepository);
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

    @Test
    @DisplayName("자기 자신을 찌르면 QUESTION400_2 예외가 발생하고 회원을 조회하지 않는다.")
    void poke_self() {
        assertThatThrownBy(() -> questionService.poke(MEMBER_ID, MEMBER_ID))
                .isInstanceOf(QuestionException.class)
                .extracting("errorCode")
                .isEqualTo(QuestionErrorCode.SELF_POKE);
        verify(memberRepository, never()).findById(any());
    }

    @Test
    @DisplayName("찌르기 요청자가 존재하지 않는 회원이면 MEMBER404_1 예외가 발생한다.")
    void poke_memberNotFound() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> questionService.poke(MEMBER_ID, RECEIVER_ID))
                .isInstanceOf(MemberException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("가족이 없는 회원이 찌르면 QUESTION403_1 예외가 발생하고 대상을 조회하지 않는다.")
    void poke_noFamily() {
        Member memberWithoutFamily = Member.builder().id(MEMBER_ID).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(memberWithoutFamily));

        assertThatThrownBy(() -> questionService.poke(MEMBER_ID, RECEIVER_ID))
                .isInstanceOf(QuestionException.class)
                .extracting("errorCode")
                .isEqualTo(QuestionErrorCode.FAMILY_REQUIRED);
        verify(memberRepository, never()).findById(RECEIVER_ID);
    }

    @Test
    @DisplayName("찌를 대상이 존재하지 않는 회원이면 QUESTION404_3 예외가 발생한다.")
    void poke_receiverNotFound() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(memberRepository.findById(RECEIVER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> questionService.poke(MEMBER_ID, RECEIVER_ID))
                .isInstanceOf(QuestionException.class)
                .extracting("errorCode")
                .isEqualTo(QuestionErrorCode.POKE_RECEIVER_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 가족의 회원을 찌르면 없는 회원과 같은 QUESTION404_3 예외가 발생하고 저장하지 않는다.")
    void poke_receiverOtherFamily() {
        Family otherFamily = Family.builder().id(2L).name("옆집").build();
        Member otherFamilyMember = Member.builder().id(RECEIVER_ID).family(otherFamily).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(memberRepository.findById(RECEIVER_ID)).willReturn(Optional.of(otherFamilyMember));

        assertThatThrownBy(() -> questionService.poke(MEMBER_ID, RECEIVER_ID))
                .isInstanceOf(QuestionException.class)
                .extracting("errorCode")
                .isEqualTo(QuestionErrorCode.POKE_RECEIVER_NOT_FOUND);
        verify(pokeRepository, never()).save(any());
    }

    @Test
    @DisplayName("가족이 없는 회원을 찌르면 QUESTION404_3 예외가 발생한다.")
    void poke_receiverNoFamily() {
        Member receiverWithoutFamily = Member.builder().id(RECEIVER_ID).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(memberRepository.findById(RECEIVER_ID)).willReturn(Optional.of(receiverWithoutFamily));

        assertThatThrownBy(() -> questionService.poke(MEMBER_ID, RECEIVER_ID))
                .isInstanceOf(QuestionException.class)
                .extracting("errorCode")
                .isEqualTo(QuestionErrorCode.POKE_RECEIVER_NOT_FOUND);
    }

    @Test
    @DisplayName("오늘 이미 찌른 대상이면 QUESTION409_2 예외가 발생하고 저장하지 않으며 이벤트도 발행하지 않는다.")
    void poke_alreadyPoked() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(memberRepository.findById(RECEIVER_ID)).willReturn(Optional.of(receiver));
        given(pokeRepository.existsBySenderAndReceiverAndPokeDate(member, receiver, today)).willReturn(true);

        assertThatThrownBy(() -> questionService.poke(MEMBER_ID, RECEIVER_ID))
                .isInstanceOf(QuestionException.class)
                .extracting("errorCode")
                .isEqualTo(QuestionErrorCode.ALREADY_POKED);
        verify(pokeRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("같은 가족 구성원을 찌르면 보낸 사람, 받은 사람, 오늘 날짜로 저장하고 찌르기 이벤트를 발행한다.")
    void poke_success() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(memberRepository.findById(RECEIVER_ID)).willReturn(Optional.of(receiver));
        given(pokeRepository.existsBySenderAndReceiverAndPokeDate(member, receiver, today)).willReturn(false);

        questionService.poke(MEMBER_ID, RECEIVER_ID);

        // save에 넘어간 엔티티를 붙잡아 값이 제대로 채워졌는지 확인
        ArgumentCaptor<Poke> captor = ArgumentCaptor.forClass(Poke.class);
        verify(pokeRepository).save(captor.capture());
        Poke saved = captor.getValue();
        assertThat(saved.getSender()).isEqualTo(member);
        assertThat(saved.getReceiver()).isEqualTo(receiver);
        assertThat(saved.getPokeDate()).isEqualTo(today);

        verify(eventPublisher).publishEvent(new PokeEvent(MEMBER_ID, RECEIVER_ID));
    }

    @Test
    @DisplayName("동시 요청으로 찌르기 유니크 제약을 위반하면 순차 중복과 같은 QUESTION409_2 예외로 바꿔 던지고 이벤트를 발행하지 않는다.")
    void poke_concurrentDuplicate() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(memberRepository.findById(RECEIVER_ID)).willReturn(Optional.of(receiver));
        given(pokeRepository.existsBySenderAndReceiverAndPokeDate(member, receiver, today)).willReturn(false);
        given(pokeRepository.save(any(Poke.class))).willThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new PSQLException(new ServerErrorMessage(
                        "SERROR\0C23505\0Mduplicate key value violates unique constraint\0nuk_poke_sender_receiver_date\0"))));

        assertThatThrownBy(() -> questionService.poke(MEMBER_ID, RECEIVER_ID))
                .isInstanceOf(QuestionException.class)
                .extracting("errorCode")
                .isEqualTo(QuestionErrorCode.ALREADY_POKED);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("찌르기 유니크 제약이 아닌 무결성 위반은 바꾸지 않고 그대로 던진다.")
    void poke_otherIntegrityViolation() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(memberRepository.findById(RECEIVER_ID)).willReturn(Optional.of(receiver));
        given(pokeRepository.existsBySenderAndReceiverAndPokeDate(member, receiver, today)).willReturn(false);
        // 대상 회원이 그 사이 탈퇴해 FK 위반이 난 상황
        given(pokeRepository.save(any(Poke.class))).willThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new PSQLException(new ServerErrorMessage(
                        "SERROR\0C23503\0Minsert or update violates foreign key constraint\0nfk_poke_receiver\0"))));

        assertThatThrownBy(() -> questionService.poke(MEMBER_ID, RECEIVER_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("배정 대상 가족이 없으면 질문을 배정하지 않는다.")
    void assign_familyNotFound() {
        given(familyRepository.findById(1L)).willReturn(Optional.empty());

        questionService.assign(1L, today);

        verify(familyQuestionRepository, never()).save(any(FamilyQuestion.class));
    }

    @Test
    @DisplayName("이미 질문이 배정된 날짜면 다시 배정하지 않는다.")
    void assign_alreadyAssigned() {
        Family createdFamily = familyCreatedOn(today);
        given(familyRepository.findById(1L)).willReturn(Optional.of(createdFamily));
        given(familyQuestionRepository.existsByFamilyAndQuestionDate(createdFamily, today)).willReturn(true);

        questionService.assign(1L, today);

        verify(familyQuestionRepository, never()).save(any(FamilyQuestion.class));
    }

    @Test
    @DisplayName("가족 생성일 이전 날짜면 배정하지 않는다.")
    void assign_beforeCreated() {
        Family createdFamily = familyCreatedOn(today);
        given(familyRepository.findById(1L)).willReturn(Optional.of(createdFamily));

        questionService.assign(1L, today.minusDays(1));

        verify(familyQuestionRepository, never()).save(any(FamilyQuestion.class));
    }

    @Test
    @DisplayName("가족 생성일(1일차)에는 1일차 고정 질문을 배정한다.")
    void assign_day1Fixed() {
        Family createdFamily = familyCreatedOn(today);
        Question fixed = Question.builder().id(1L).fixedDay(1).build();
        given(familyRepository.findById(1L)).willReturn(Optional.of(createdFamily));
        given(questionRepository.findByFixedDayAndIsActiveTrue(1)).willReturn(Optional.of(fixed));

        questionService.assign(1L, today);

        FamilyQuestion saved = captureSavedFamilyQuestion();
        assertThat(saved.getQuestion()).isEqualTo(fixed);
        assertThat(saved.getFamily()).isEqualTo(createdFamily);
        assertThat(saved.getQuestionDate()).isEqualTo(today);
    }

    @Test
    @DisplayName("7일차에는 7일차 고정 질문을 배정한다.")
    void assign_day7Fixed() {
        Family createdFamily = familyCreatedOn(today.minusDays(6));
        Question fixed = Question.builder().id(7L).fixedDay(7).build();
        given(familyRepository.findById(1L)).willReturn(Optional.of(createdFamily));
        given(questionRepository.findByFixedDayAndIsActiveTrue(7)).willReturn(Optional.of(fixed));

        questionService.assign(1L, today);

        assertThat(captureSavedFamilyQuestion().getQuestion()).isEqualTo(fixed);
    }

    @Test
    @DisplayName("8일차에는 고정 질문을 조회하지 않고 질문 풀에서 배정한다.")
    void assign_day8Pool() {
        Family createdFamily = familyCreatedOn(today.minusDays(7));
        Question pool = Question.builder().id(200L).build();
        given(familyRepository.findById(1L)).willReturn(Optional.of(createdFamily));
        given(questionRepository.findUnassignedPoolQuestions(createdFamily)).willReturn(List.of(pool));

        questionService.assign(1L, today);

        assertThat(captureSavedFamilyQuestion().getQuestion()).isEqualTo(pool);
        verify(questionRepository, never()).findByFixedDayAndIsActiveTrue(any());
    }

    @Test
    @DisplayName("고정 질문이 없거나 비활성화됐으면 질문 풀에서 대체 배정한다.")
    void assign_fixedMissingFallbackToPool() {
        Family createdFamily = familyCreatedOn(today.minusDays(2));
        Question pool = Question.builder().id(200L).build();
        given(familyRepository.findById(1L)).willReturn(Optional.of(createdFamily));
        given(questionRepository.findByFixedDayAndIsActiveTrue(3)).willReturn(Optional.empty());
        given(questionRepository.findUnassignedPoolQuestions(createdFamily)).willReturn(List.of(pool));

        questionService.assign(1L, today);

        assertThat(captureSavedFamilyQuestion().getQuestion()).isEqualTo(pool);
    }

    @Test
    @DisplayName("미사용 질문 중 가족이 가장 적게 받은 취향 종류의 질문을 배정하며, 일반 질문도 하나의 종류로 집계한다.")
    void assign_leastReceivedTasteType() {
        Family createdFamily = familyCreatedOn(today.minusDays(10));
        Question general = Question.builder().id(201L).build();
        Question category = Question.builder().id(202L).tasteType(TasteType.CATEGORY).build();
        Question price = Question.builder().id(203L).tasteType(TasteType.PRICE).build();
        given(familyRepository.findById(1L)).willReturn(Optional.of(createdFamily));
        given(questionRepository.findUnassignedPoolQuestions(createdFamily)).willReturn(List.of(general, category, price));
        given(familyQuestionRepository.countAssignedByTasteType(createdFamily)).willReturn(List.of(
                tasteTypeCount(null, 5L),
                tasteTypeCount(TasteType.CATEGORY, 2L),
                tasteTypeCount(TasteType.PRICE, 3L)));

        questionService.assign(1L, today);

        assertThat(captureSavedFamilyQuestion().getQuestion()).isEqualTo(category);
    }

    @Test
    @DisplayName("한 번도 받지 않은 취향 종류는 0회로 보고 우선 배정한다.")
    void assign_neverReceivedTasteType() {
        Family createdFamily = familyCreatedOn(today.minusDays(10));
        Question general = Question.builder().id(201L).build();
        Question steps = Question.builder().id(204L).tasteType(TasteType.STEPS).build();
        given(familyRepository.findById(1L)).willReturn(Optional.of(createdFamily));
        given(questionRepository.findUnassignedPoolQuestions(createdFamily)).willReturn(List.of(general, steps));
        given(familyQuestionRepository.countAssignedByTasteType(createdFamily)).willReturn(List.of(
                tasteTypeCount(null, 1L)));

        questionService.assign(1L, today);

        assertThat(captureSavedFamilyQuestion().getQuestion()).isEqualTo(steps);
    }

    @Test
    @DisplayName("질문 풀을 모두 받았으면 가장 오래전에 받은 질문을 다시 배정한다.")
    void assign_poolExhaustedReassignOldest() {
        Family createdFamily = familyCreatedOn(today.minusDays(30));
        Question oldest = Question.builder().id(201L).build();
        Question recent = Question.builder().id(202L).build();
        given(familyRepository.findById(1L)).willReturn(Optional.of(createdFamily));
        given(questionRepository.findUnassignedPoolQuestions(createdFamily)).willReturn(List.of());
        given(questionRepository.findAssignedPoolQuestionsOrderByLastAssigned(createdFamily))
                .willReturn(List.of(oldest, recent));

        questionService.assign(1L, today);

        assertThat(captureSavedFamilyQuestion().getQuestion()).isEqualTo(oldest);
    }

    @Test
    @DisplayName("배정 가능한 질문이 하나도 없으면 배정하지 않는다.")
    void assign_noQuestion() {
        Family createdFamily = familyCreatedOn(today.minusDays(30));
        given(familyRepository.findById(1L)).willReturn(Optional.of(createdFamily));
        given(questionRepository.findUnassignedPoolQuestions(createdFamily)).willReturn(List.of());
        given(questionRepository.findAssignedPoolQuestionsOrderByLastAssigned(createdFamily)).willReturn(List.of());

        questionService.assign(1L, today);

        verify(familyQuestionRepository, never()).save(any(FamilyQuestion.class));
    }

    private Family familyCreatedOn(LocalDate createdDate) {
        Family createdFamily = Family.builder().id(1L).name("행복한 우리집").build();
        ReflectionTestUtils.setField(createdFamily, "createdAt", createdDate.atTime(9, 0));
        return createdFamily;
    }

    private FamilyQuestion captureSavedFamilyQuestion() {
        ArgumentCaptor<FamilyQuestion> captor = ArgumentCaptor.forClass(FamilyQuestion.class);
        verify(familyQuestionRepository).save(captor.capture());
        return captor.getValue();
    }

    private TasteTypeCount tasteTypeCount(TasteType tasteType, Long assignedCount) {
        return new TasteTypeCount() {
            @Override
            public TasteType getTasteType() {
                return tasteType;
            }

            @Override
            public Long getAssignedCount() {
                return assignedCount;
            }
        };
    }
}
