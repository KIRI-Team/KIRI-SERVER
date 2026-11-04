package com.kiri.server.domain.question.event;

import com.kiri.server.domain.family.event.FamilyCreatedEvent;
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

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FamilyCreatedEventListenerTest {

    private static final Long FAMILY_ID = 1L;

    @Mock
    private QuestionService questionService;

    private FamilyCreatedEventListener listener;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-10T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final LocalDate today = LocalDate.now(clock);

    @BeforeEach
    void setUp() {
        listener = new FamilyCreatedEventListener(questionService, clock);
    }

    @Test
    @DisplayName("가족이 생성되면 오늘과 내일 질문을 배정한다.")
    void handle_assignTodayAndTomorrow() {
        listener.handle(new FamilyCreatedEvent(FAMILY_ID));

        verify(questionService).assign(FAMILY_ID, today);
        verify(questionService).assign(FAMILY_ID, today.plusDays(1));
    }

    @Test
    @DisplayName("오늘 질문 배정이 실패해도 예외를 던지지 않고 내일 질문 배정을 시도한다.")
    void handle_todayFailed() {
        willThrow(new RuntimeException("배정 실패")).given(questionService).assign(FAMILY_ID, today);

        listener.handle(new FamilyCreatedEvent(FAMILY_ID));

        verify(questionService).assign(FAMILY_ID, today.plusDays(1));
    }
}
