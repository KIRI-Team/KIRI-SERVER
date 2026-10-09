package com.kiri.server.global.logging;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

public class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("비동기 스레드에 요청 스레드의 traceId가 전달된다.")
    void decorate_propagatesMdc() throws InterruptedException {
        MDC.put(RequestLoggingFilter.TRACE_ID, "test-trace-id");
        AtomicReference<String> traceIdInSync = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> traceIdInSync.set(MDC.get(RequestLoggingFilter.TRACE_ID)));
        Thread thread = new Thread(decorated);
        thread.start();
        thread.join();

        assertThat(traceIdInSync.get()).isEqualTo("test-trace-id");
    }

    @Test
    @DisplayName("작업이 끝나면 비동기 스레드의 MDC를 비운다.")
    void decorate_clearsMdcAfterRun() throws InterruptedException {
        MDC.put(RequestLoggingFilter.TRACE_ID, "test-trace-id");
        AtomicReference<String> traceIdAfterRun = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> {});
        Thread thread = new Thread(() -> {
            decorated.run();
            traceIdAfterRun.set(MDC.get(RequestLoggingFilter.TRACE_ID));
        });
        thread.start();
        thread.join();

        assertThat(traceIdAfterRun.get()).isNull();
    }
}
