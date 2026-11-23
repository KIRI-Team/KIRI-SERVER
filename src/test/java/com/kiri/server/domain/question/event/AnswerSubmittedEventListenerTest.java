package com.kiri.server.domain.question.event;

import com.kiri.server.domain.question.service.AnswerClassificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AnswerSubmittedEventListenerTest {

    private final AnswerClassificationService service = mock(AnswerClassificationService.class);
    private final AnswerSubmittedEventListener listener = new AnswerSubmittedEventListener(service);

    @Test
    @DisplayName("분류가 실패해도 예외를 밖으로 던지지 않는다")
    void swallowFailure() {
        doThrow(new RuntimeException("AI 실패")).when(service).classify(1L);

        assertThatCode(() -> listener.classify(new AnswerSubmittedEvent(1L))).doesNotThrowAnyException();
        verify(service).classify(1L);
    }

    @Test
    @DisplayName("답변 ID가 없으면 분류하지 않는다")
    void skipNullId() {
        listener.classify(new AnswerSubmittedEvent(null));

        verify(service, never()).classify(any());
    }
}
