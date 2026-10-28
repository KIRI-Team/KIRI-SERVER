package com.kiri.server.domain.question.converter;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.question.dto.QuestionResDTO;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import com.kiri.server.domain.question.entity.MemberAnswer;
import com.kiri.server.domain.question.entity.Poke;
import com.kiri.server.domain.question.entity.Question;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class QuestionConverter {

    // questionId는 Question이 아닌 FamilyQuestion의 ID (이후 답변 API path에 사용)
    public static QuestionResDTO.TodayQuestion toTodayQuestion(
            FamilyQuestion familyQuestion, Optional<MemberAnswer> answer) {
        return QuestionResDTO.TodayQuestion.builder()
                .hasFamily(true)
                .questionId(familyQuestion.getId())
                .question(familyQuestion.getQuestion().getContent())
                .isAnswered(answer.isPresent())
                .answer(answer.map(MemberAnswer::getContent).orElse(null))
                .build();
    }

    // 가족이 없으면 질문이 배정되지 않으므로 나머지 값은 비워 둠
    public static QuestionResDTO.TodayQuestion toNoFamily() {
        return QuestionResDTO.TodayQuestion.builder()
                .hasFamily(false)
                .isAnswered(false)
                .build();
    }

    public static MemberAnswer toMemberAnswer(String content, FamilyQuestion familyQuestion, Member member) {
        return MemberAnswer.builder()
                .content(content)
                .familyQuestion(familyQuestion)
                .member(member)
                .build();
    }

    public static QuestionResDTO.FamilyAnswers toFamilyAnswers(
            FamilyQuestion familyQuestion, List<Member> familyMembers, Map<Long, MemberAnswer> answerByMemberId, Long myId
    ) {
        List<QuestionResDTO.FamilyAnswer> answers = familyMembers.stream()
                .map(familyMember -> toFamilyAnswer(familyMember, answerByMemberId.get(familyMember.getId()), myId))
                .toList();

        return QuestionResDTO.FamilyAnswers.builder()
                .question(familyQuestion.getQuestion().getContent())
                .answers(answers)
                .build();
    }

    private static QuestionResDTO.FamilyAnswer toFamilyAnswer(Member member, MemberAnswer answer, Long myId) {

        return QuestionResDTO.FamilyAnswer.builder()
                .isMe(member.getId().equals(myId))
                .memberId(member.getId())
                .nickname(member.getNickname())
                .isAnswered(answer != null)
                .answer(answer == null ? null : answer.getContent())
                .build();
    }

    public static Poke toPoke(Member sender, Member receiver, LocalDate pokeDate) {
        return Poke.builder()
                .sender(sender)
                .receiver(receiver)
                .pokeDate(pokeDate)
                .build();
    }

    public static FamilyQuestion toFamilyQuestion(Family family, Question question, LocalDate questionDate) {
        return FamilyQuestion.builder()
                .family(family)
                .question(question)
                .questionDate(questionDate)
                .build();
    }
}
