package com.kiri.server.global.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class ScheduledTraceAspectTest {

    // 기록은 원본 객체(target)에서 읽고, 호출은 Aspect가 적용된 프록시(job)로 함
    // (CGLIB 프록시의 필드는 원본과 별개라 프록시에서 읽으면 null)
    private TestJob target;
    private TestJob job;

    @BeforeEach
    void setUp() {
        target = new TestJob();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new ScheduledTraceAspect());
        job = factory.getProxy();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("@Scheduled 실행 중에는 sch- 접두사 traceId가 MDC에 있다.")
    void trace_setsScheduledTraceId() {
        job.run();

        assertThat(target.traceIds).hasSize(1);
        assertThat(target.traceIds.get(0)).matches("^sch-[0-9a-f]{12}$");
    }

    @Test
    @DisplayName("실행이 끝나면 MDC에서 traceId를 제거한다.")
    void trace_removesTraceIdAfterRun() {
        job.run();

        assertThat(MDC.get(RequestLoggingFilter.TRACE_ID)).isNull();
    }

    @Test
    @DisplayName("실행마다 새로운 traceId를 발급한다.")
    void trace_issuesNewTraceIdEachRun() {
        job.run();
        job.run();

        assertThat(target.traceIds).hasSize(2);
        assertThat(target.traceIds.get(0)).isNotEqualTo(target.traceIds.get(1));
    }

    @Test
    @DisplayName("완료 로그에 작업 이름을 남긴다.")
    void trace_logsJobName(CapturedOutput output) {
        job.run();

        assertThat(output).contains("[SCHEDULED] TestJob.run");
    }

    @Test
    @DisplayName("예외가 나면 실패 로그를 남기고 예외를 그대로 던지며 MDC를 비운다.")
    void trace_logsFailureAndRethrows(CapturedOutput output) {
        assertThatThrownBy(() -> job.fail()).isInstanceOf(IllegalStateException.class);

        assertThat(output).contains("[SCHEDULED] TestJob.fail FAILED");
        assertThat(output).contains("IllegalStateException");
        assertThat(MDC.get(RequestLoggingFilter.TRACE_ID)).isNull();
    }

    @Test
    @DisplayName("@Scheduled가 없는 메서드는 traceId를 발급하지 않는다.")
    void trace_skipsNonScheduledMethod() {
        job.notScheduled();

        assertThat(target.traceIds).containsExactly((String) null);
    }

    static class TestJob {

        final List<String> traceIds = new ArrayList<>();

        @Scheduled(fixedDelay = 1000)
        public void run() {
            traceIds.add(MDC.get(RequestLoggingFilter.TRACE_ID));
        }

        @Scheduled(fixedDelay = 1000)
        public void fail() {
            throw new IllegalStateException("test");
        }

        public void notScheduled() {
            traceIds.add(MDC.get(RequestLoggingFilter.TRACE_ID));
        }
    }
}
