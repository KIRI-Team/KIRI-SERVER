package com.kiri.server.global.logging;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Aspect
@Component
public class ScheduledTraceAspect {

    private static final String SCHEDULED_PREFIX = "sch-";

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object trace(ProceedingJoinPoint joinPoint) throws Throwable {
        String traceId = SCHEDULED_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        String job = joinPoint.getSignature().getDeclaringType().getSimpleName() + "." + joinPoint.getSignature().getName();
        long startTime = System.currentTimeMillis();

        MDC.put(RequestLoggingFilter.TRACE_ID, traceId);
        try {
            Object result = joinPoint.proceed();
            log.info("[SCHEDULED] {} ({}ms)", job, System.currentTimeMillis() - startTime);
            return result;
        } catch (Throwable e) {
            log.error("[SCHEDULED] {} FAILED ({}ms) - {}", job, System.currentTimeMillis() - startTime, e.getClass().getSimpleName());
            throw e;
        } finally {
            MDC.remove(RequestLoggingFilter.TRACE_ID);
        }
    }
}
