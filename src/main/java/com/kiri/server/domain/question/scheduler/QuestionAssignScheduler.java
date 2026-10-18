package com.kiri.server.domain.question.scheduler;

import com.kiri.server.domain.family.repository.FamilyRepository;
import com.kiri.server.domain.question.service.QuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionAssignScheduler {

    private final FamilyRepository familyRepository;
    private final QuestionService questionService;
    private final Clock clock;

    // 매일 23시에 다음 날 질문 미리 배정
    @Scheduled(cron = "${question.assign.tomorrow-cron:0 0 23 * * *}", zone = "Asia/Seoul")
    public void assignTomorrow() {
        assignAll(LocalDate.now(clock).plusDays(1));
    }

    // 전날 밤 배정이 누락된 가족 보정
    @Scheduled(cron = "${question.assign.today-cron:0 5 0 * * *}", zone = "Asia/Seoul")
    public void assignTodayIfMissing() {
        assignAll(LocalDate.now(clock));
    }

    // 한 가족이 실패해도 나머지 가족은 계속 배정
    private void assignAll(LocalDate date) {
        for (Long familyId : familyRepository.findAllIds()) {
            try {
                questionService.assign(familyId, date);
            } catch (Exception e) {
                log.error("질문 배정 실패 familyId={}, date={}", familyId, date, e);
            }
        }
    }
}
