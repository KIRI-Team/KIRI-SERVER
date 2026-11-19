package com.kiri.server.domain.question.converter;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import com.kiri.server.domain.question.entity.MemberAnswer;
import com.kiri.server.domain.question.entity.MemberAnswerCategory;

public class MemberAnswerConverter {

    public static MemberAnswer toMemberAnswer(Member member, FamilyQuestion familyQuestion, String content) {
        return MemberAnswer.builder()
                .member(member)
                .familyQuestion(familyQuestion)
                .content(content)
                .build();
    }

    public static MemberAnswerCategory toMemberAnswerCategory(MemberAnswer memberAnswer, Category category) {
        return MemberAnswerCategory.builder()
                .memberAnswer(memberAnswer)
                .category(category)
                .build();
    }
}
