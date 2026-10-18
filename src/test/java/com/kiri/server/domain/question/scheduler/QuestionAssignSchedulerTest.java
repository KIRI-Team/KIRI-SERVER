package com.kiri.server.domain.question.scheduler;

import com.kiri.server.domain.family.repository.FamilyRepository;
import com.kiri.server.domain.question.service.QuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QuestionAssignSchedulerTest {

    @Mock
    private FamilyRepository familyRepository;

    @Mock
    private QuestionService questionService;

    private QuestionAssignScheduler scheduler;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-10T14:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final LocalDate today = LocalDate.now(clock);

    @BeforeEach
    void setUp() {
        scheduler = new QuestionAssignScheduler(familyRepository, questionService, clock);
    }

    @Test
    @DisplayName("밤 배정은 모든 가족에게 다음 날 질문을 배정한다.")
    void assignTomorrow() {
        given(familyRepository.findAllIds()).willReturn(List.of(1L, 2L));

        scheduler.assignTomorrow();

        verify(questionService).assign(1L, today.plusDays(1));
        verify(questionService).assign(2L, today.plusDays(1));
    }

    @Test
    @DisplayName("보정 배정은 모든 가족에게 오늘 질문을 배정한다.")
    void assignTodayIfMissing() {
        given(familyRepository.findAllIds()).willReturn(List.of(1L, 2L));

        scheduler.assignTodayIfMissing();

        verify(questionService).assign(1L, today);
        verify(questionService).assign(2L, today);
    }

    @Test
    @DisplayName("한 가족의 배정이 실패해도 나머지 가족은 계속 배정한다.")
    void assignTomorrow_continueOnFailure() {
        given(familyRepository.findAllIds()).willReturn(List.of(1L, 2L));
        willThrow(new RuntimeException("배정 실패")).given(questionService).assign(1L, today.plusDays(1));

        scheduler.assignTomorrow();

        verify(questionService).assign(2L, today.plusDays(1));
    }
}
