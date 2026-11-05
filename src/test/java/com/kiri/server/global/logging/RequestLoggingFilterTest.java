package com.kiri.server.global.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
public class RequestLoggingFilterTest {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("요청 처리 중 MDC에 traceId를 저장하고, 같은 값을 응답 헤더에 담는다.")
    void doFilter_setsTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> traceIdInChain.set(MDC.get(RequestLoggingFilter.TRACE_ID)));

        assertThat(traceIdInChain.get()).isNotBlank();
        assertThat(response.getHeader(TRACE_ID_HEADER)).isEqualTo(traceIdInChain.get());

    }

    @Test
    @DisplayName("요청 처리가 끝나면 MDC에서 traceId를 제거한다.")
    void doFilter_removesTraceIdAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertThat(MDC.get(RequestLoggingFilter.TRACE_ID)).isNull();
    }

    @Test
    @DisplayName("요청 처리 중 예외가 발생해도 MDC에서 traceId를 제거한다.")
    void doFilter_removesTraceIdWhenExceptionThrown() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            throw new RuntimeException("test");
        })).isInstanceOf(RuntimeException.class);

        assertThat(MDC.get(RequestLoggingFilter.TRACE_ID)).isNull();
    }

    @Test
    @DisplayName("예외가 전파되면 200이 아닌 실패로 기록한다.")
    void doFilter_logsFailureWhenExceptionThrown(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            throw new IllegalStateException("test");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(output).contains("GET /api/test FAILED");
        assertThat(output).contains("IllegalStateException");
        assertThat(output).doesNotContain("GET /api/test 200");
    }

    @Test
    @DisplayName("/actuator 요청은 traceId를 발급하지 않는다.")
    void doFilter_skipsActuator() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertThat(response.getHeader(TRACE_ID_HEADER)).isNull();
    }

    @Test
    @DisplayName("context path가 있어도 /actuator 요청은 traceId를 발급하지 않는다.")
    void doFilter_skipsActuatorWithContextPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/kiri/actuator/health");
        request.setContextPath("/kiri");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertThat(response.getHeader(TRACE_ID_HEADER)).isNull();
    }

    @Test
    @DisplayName("context path가 있는 일반 요청은 traceId를 발급한다.")
    void doFilter_setsTraceIdWithContextPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/kiri/api/test");
        request.setContextPath("/kiri");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertThat(response.getHeader(TRACE_ID_HEADER)).isNotBlank();
    }

    @Test
    @DisplayName("nginx가 넘긴 X-Request-Id가 32자리 hex면 traceId로 그대로 사용한다.")
    void doFilter_reusesNginxRequestId() throws Exception {
        String requestId = "0123456789abcdef0123456789abcdef";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader(REQUEST_ID_HEADER, requestId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> traceIdInChain.set(MDC.get(RequestLoggingFilter.TRACE_ID)));

        assertThat(traceIdInChain.get()).isEqualTo(requestId);
        assertThat(response.getHeader(TRACE_ID_HEADER)).isEqualTo(requestId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hack\"injected", "0123456789ABCDEF0123456789ABCDEF", "0123456789abcdef", "0123456789abcdef0123456789abcdef0"})
    @DisplayName("X-Request-Id 형식이 맞지 않으면 무시하고 traceId를 새로 발급한다.")
    void doFilter_ignoresInvalidRequestId(String invalidRequestId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader(REQUEST_ID_HEADER, invalidRequestId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertThat(response.getHeader(TRACE_ID_HEADER))
                .isNotEqualTo(invalidRequestId)
                .matches("^[0-9a-f]{32}$");
    }

    @Test
    @DisplayName("X-Request-Id가 없으면 nginx와 같은 32자리 hex traceId를 발급한다.")
    void doFilter_generatesTraceIdWithoutRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertThat(response.getHeader(TRACE_ID_HEADER)).matches("^[0-9a-f]{32}$");
    }

}
