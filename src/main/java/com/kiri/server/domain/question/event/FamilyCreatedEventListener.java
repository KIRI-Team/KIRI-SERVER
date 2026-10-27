package com.kiri.server.domain.question.event;

import com.kiri.server.domain.family.event.FamilyCreatedEvent;
import com.kiri.server.domain.question.service.QuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class FamilyCreatedEventListener {

    private final QuestionService questionService;
    private final Clock clock;

    // 가족 생성 커밋 후 1일차, 2일차 질문 배정
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(FamilyCreatedEvent event) {
        LocalDate today = LocalDate.now(clock);
        assign(event.familyId(), today);
        assign(event.familyId(), today.plusDays(1));
    }

    // 오늘 배정이 실패해도 내일 배정은 시도 (누락분은 00:05 보정 배정)
    private void assign(Long familyId, LocalDate date) {
        try {
            questionService.assign(familyId, date);
        } catch (Exception e) {
            log.error("가족 생성 질문 배정 실패 familyId={}, date={}", familyId, date, e);
        }
    }
}
