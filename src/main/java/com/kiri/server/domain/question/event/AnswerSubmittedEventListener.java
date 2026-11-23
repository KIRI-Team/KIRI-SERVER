package com.kiri.server.domain.question.event;

import com.kiri.server.domain.question.service.AnswerClassificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnswerSubmittedEventListener {

    private final AnswerClassificationService answerClassificationService;

    // 답변 저장이 커밋된 뒤 별도 스레드에서 분류한다. 분류가 실패해도 답변 저장 응답에는 영향이 없다.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void classify(AnswerSubmittedEvent event) {
        if (event.memberAnswerId() == null) {
            return;
        }
        try {
            answerClassificationService.classify(event.memberAnswerId());
        } catch (Exception e) {
            log.warn("답변 취향 분류 실패 memberAnswerId={}", event.memberAnswerId(), e);
        }
    }
}
