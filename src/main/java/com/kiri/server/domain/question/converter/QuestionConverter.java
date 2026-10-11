package com.kiri.server.domain.question.converter;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.question.dto.QuestionResDTO;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import com.kiri.server.domain.question.entity.MemberAnswer;

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
}
