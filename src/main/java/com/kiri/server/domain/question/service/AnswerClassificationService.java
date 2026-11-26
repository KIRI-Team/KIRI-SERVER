package com.kiri.server.domain.question.service;

import com.kiri.server.domain.question.converter.MemberAnswerConverter;
import com.kiri.server.domain.question.dto.PreferenceClassification;
import com.kiri.server.domain.question.entity.MemberAnswer;
import com.kiri.server.domain.question.entity.MemberAnswerCategory;
import com.kiri.server.domain.question.entity.Question;
import com.kiri.server.domain.question.enums.TasteType;
import com.kiri.server.domain.question.repository.MemberAnswerCategoryRepository;
import com.kiri.server.domain.question.repository.MemberAnswerRepository;
import com.kiri.server.global.ai.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 취향 질문 답변을 AI로 분류해 답변(실내외·소요시간·비용·활동량)과 카테고리에 저장한다.
 * AI 호출은 DB 트랜잭션 밖에서 하고, 읽기와 쓰기만 각각 짧은 트랜잭션으로 묶는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerClassificationService {

    private final MemberAnswerRepository memberAnswerRepository;
    private final MemberAnswerCategoryRepository memberAnswerCategoryRepository;
    private final AiService aiService;
    private final TransactionTemplate transactionTemplate;

    public void classify(Long memberAnswerId) {

        Target target = transactionTemplate.execute(status -> loadTarget(memberAnswerId));

        // 답변이 지워졌거나 취향 질문이 아니면 분류하지 않는다
        if (target == null) {
            return;
        }

        PreferenceClassification classification = aiService.classify(target.tasteType(), target.question(), target.answer());

        transactionTemplate.executeWithoutResult(status -> apply(memberAnswerId, target, classification));
    }

    private Target loadTarget(Long memberAnswerId) {
        return memberAnswerRepository.findById(memberAnswerId)
                .map(answer -> {
                    Question question = answer.getFamilyQuestion().getQuestion();
                    if (question.getTasteType() == null) {
                        return null;
                    }
                    return new Target(question.getTasteType(), question.getContent(), answer.getContent());
                })
                .orElse(null);
    }

    private void apply(Long memberAnswerId, Target target, PreferenceClassification classification) {
        MemberAnswer answer = memberAnswerRepository.findById(memberAnswerId).orElse(null);

        if (answer == null) {
            return;
        }

        // AI를 기다리는 사이 답변이 다시 수정됐으면 옛 내용의 결과로 덮지 않는다 (새 내용은 다음 이벤트가 분류)
        if (!answer.getContent().equals(target.answer())) {
            log.info("분류 중 답변이 수정되어 결과를 버림 memberAnswerId={}", memberAnswerId);
            return;
        }

        answer.updateClassification(classification.indoorOutdoor(), classification.durationLevel(), classification.priceLevel(), classification.activityLevel());

        if (target.tasteType() == TasteType.CATEGORY) {
            memberAnswerCategoryRepository.deleteAllByMemberAnswerId(answer.getId());

            List<MemberAnswerCategory> categories = classification.categories().stream()
                    .map(category -> MemberAnswerConverter.toMemberAnswerCategory(answer, category))
                    .toList();

            memberAnswerCategoryRepository.saveAll(categories);
        }
    }

    private record Target(TasteType tasteType, String question, String answer) {
    }
}
