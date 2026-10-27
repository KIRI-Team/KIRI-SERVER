package com.kiri.server.domain.question.converter;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.question.dto.QuestionResDTO;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import com.kiri.server.domain.question.entity.Poke;
import com.kiri.server.domain.question.entity.Question;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionConverterTest {

    @Test
    @DisplayName("오늘의 질문 응답의 questionId는 질문 ID가 아닌 가족 질문 ID다.")
    void toTodayQuestion_questionIdIsFamilyQuestionId() {
        Question question = Question.builder().id(100L).content("오늘 가장 웃겼던 일은?").build();
        FamilyQuestion familyQuestion = FamilyQuestion.builder().id(10L).question(question).build();

        QuestionResDTO.TodayQuestion result = QuestionConverter.toTodayQuestion(familyQuestion, Optional.empty());

        assertThat(result.questionId()).isEqualTo(10L);
        assertThat(result.question()).isEqualTo("오늘 가장 웃겼던 일은?");
    }

    @Test
    @DisplayName("가족 없음 응답은 hasFamily와 isAnswered가 false이고 나머지 값은 null이다.")
    void toNoFamily() {
        QuestionResDTO.TodayQuestion result = QuestionConverter.toNoFamily();

        assertThat(result.hasFamily()).isFalse();
        assertThat(result.isAnswered()).isFalse();
        assertThat(result.questionId()).isNull();
        assertThat(result.question()).isNull();
        assertThat(result.answer()).isNull();
    }

    @Test
    @DisplayName("찌르기 엔티티는 보낸 사람, 받은 사람, 찌른 날짜를 그대로 담는다.")
    void toPoke() {
        Member sender = Member.builder().id(1L).build();
        Member receiver = Member.builder().id(2L).build();
        LocalDate pokeDate = LocalDate.of(2026, 10, 10);

        Poke result = QuestionConverter.toPoke(sender, receiver, pokeDate);

        assertThat(result.getSender()).isEqualTo(sender);
        assertThat(result.getReceiver()).isEqualTo(receiver);
        assertThat(result.getPokeDate()).isEqualTo(pokeDate);
    }
}
