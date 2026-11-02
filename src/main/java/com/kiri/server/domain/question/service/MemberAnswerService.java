package com.kiri.server.domain.question.service;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.question.converter.MemberAnswerConverter;
import com.kiri.server.domain.question.dto.PreferenceClassification;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import com.kiri.server.domain.question.entity.MemberAnswer;
import com.kiri.server.domain.question.entity.MemberAnswerCategory;
import com.kiri.server.domain.question.enums.TasteType;
import com.kiri.server.domain.question.exception.QuestionException;
import com.kiri.server.domain.question.exception.code.QuestionErrorCode;
import com.kiri.server.domain.question.repository.FamilyQuestionRepository;
import com.kiri.server.domain.question.repository.MemberAnswerCategoryRepository;
import com.kiri.server.domain.question.repository.MemberAnswerRepository;
import com.kiri.server.global.ai.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberAnswerService {

    private final MemberRepository memberRepository;
    private final MemberAnswerRepository memberAnswerRepository;
    private final MemberAnswerCategoryRepository memberAnswerCategoryRepository;
    private final FamilyQuestionRepository familyQuestionRepository;
    private final AiService aiService;

    public void saveAnswer(Long memberId, Long familyQuestionId, String content) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));

        FamilyQuestion familyQuestion = familyQuestionRepository.findByIdForUpdate(familyQuestionId)
                .orElseThrow(() -> new QuestionException(QuestionErrorCode.FAMILY_QUESTION_NOT_FOUND));

        MemberAnswer answer = MemberAnswerConverter.toMemberAnswer(member, familyQuestion, content);

        memberAnswerRepository.save(answer);

        String question = familyQuestion.getQuestion().getContent();
        TasteType tasteType = familyQuestion.getQuestion().getTasteType();

        if (tasteType == null) {
            return;
        }

        PreferenceClassification classification = aiService.classify(tasteType, question, content);

        answer.updateClassification(classification.indoorOutdoor(), classification.durationLevel(), classification.priceLevel(), classification.activityLevel());

        if (tasteType == TasteType.CATEGORY) {

            List<MemberAnswerCategory> categories =
                    classification.categories().stream()
                            .map(category -> MemberAnswerConverter.toMemberAnswerCategory(answer, category))
                            .toList();

            memberAnswerCategoryRepository.saveAll(categories);
        }
    }
}
