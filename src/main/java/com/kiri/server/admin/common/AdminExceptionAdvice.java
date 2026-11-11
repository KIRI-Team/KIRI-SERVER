package com.kiri.server.admin.common;

import com.kiri.server.admin.content.dto.MissionFormDTO;
import com.kiri.server.global.apiPayload.ApiResponse;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

// 관리자 API 전용 예외 처리. 전역 핸들러보다 먼저 적용된다.
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.kiri.server.admin")
public class AdminExceptionAdvice {

    @ExceptionHandler(AdminValidationException.class)
    public ResponseEntity<ApiResponse<List<MissionFormDTO.FieldError>>> handleValidation(AdminValidationException ex) {
        log.warn("[AdminValidationException] {}", ex.getErrors());
        AdminErrorCode code = AdminErrorCode.VALIDATION_FAILED;
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.onFailure(code, ex.getErrors()));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ApiResponse<Void>> handleConcurrent(RuntimeException ex) {
        log.warn("[AdminConcurrentModification] {}", ex.getClass().getSimpleName());
        AdminErrorCode code = AdminErrorCode.CONCURRENT_MODIFICATION;
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.onFailure(code));
    }
}
