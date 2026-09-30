package com.kiri.server.global.apiPayload.handler;

import com.kiri.server.global.apiPayload.ApiResponse;
import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import com.kiri.server.global.apiPayload.code.GeneralErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GeneralExceptionAdvice extends ResponseEntityExceptionHandler {

    // 프로젝트에서 발생한 에러
    @ExceptionHandler(ProjectException.class)
    public ResponseEntity<ApiResponse<Void>> handleProjectException(ProjectException ex) {
        BaseErrorCode errorCode = ex.getErrorCode();
        if (ex.getCause() == null) {
            log.warn("[{}] {}: {}", ex.getClass().getSimpleName(), errorCode.getCode(), errorCode.getMessage());
        } else if (errorCode.getHttpStatus().is4xxClientError()) {
            log.warn("[{}] {}: {} (cause={})", ex.getClass().getSimpleName(), errorCode.getCode(), errorCode.getMessage(), causeDetail(ex.getCause()));
        } else {
            log.error("[{}] {}: {}", ex.getClass().getSimpleName(), errorCode.getCode(), errorCode.getMessage(), ex);
        }

        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.onFailure(errorCode));
    }

    // @Validated 경로/쿼리 파라미터 검증
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolationException(ConstraintViolationException ex) {
        log.warn("[ConstraintViolationException] 경로/쿼리 파라미터 검증 실패: {}", ex.getMessage());
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String path = violation.getPropertyPath().toString();
            String field = path.substring(path.lastIndexOf('.') + 1);
            errors.put(field, violation.getMessage());
        });

        BaseErrorCode errorCode = GeneralErrorCode.BAD_REQUEST;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.onFailure(errorCode, errors));

    }

    // 유니크 제약
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        BaseErrorCode errorCode = GeneralErrorCode.CONFLICT;
        String detail = ex.getCause() != null ? causeDetail(ex.getCause()) : "원인 없음";
        log.warn("[DataIntegrityViolationException] {}: {}", errorCode.getCode(), detail);

        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.onFailure(errorCode));
    }

    // 서버 내부 에러
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(Exception ex) {
        log.error("[UnexpectedException] 서버 내부 에러: ", ex);

        BaseErrorCode errorCode = GeneralErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.onFailure(errorCode));

    }

    // 오버라이드
    // @Valid(DTO 검증 실패)
    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("[MethodArgumentNotValidException] @Valid 검증 실패: {}", ex.getMessage());
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        BaseErrorCode errorCode = GeneralErrorCode.BAD_REQUEST;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .headers(headers)
                .body(ApiResponse.onFailure(errorCode, errors));
    }

    // JSON 파싱 실패
    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Throwable cause = ex.getCause();
        log.warn("[HttpMessageNotReadableException] JSON 파싱 실패: cause={}", cause != null ? cause.getClass().getSimpleName() : "unknown");

        BaseErrorCode errorCode = GeneralErrorCode.BAD_REQUEST;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .headers(headers)
                .body(ApiResponse.onFailure(errorCode, "요청 Body 형식이 잘못되었습니다."));
    }

    // 쿼리 파라미터 누락
    @Override
    protected @Nullable ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("[MissingServletRequestParameterException] 필수 파라미터 누락: {}", ex.getMessage());

        BaseErrorCode errorCode = GeneralErrorCode.BAD_REQUEST;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .headers(headers)
                .body(ApiResponse.onFailure(errorCode));
    }

    // 잘못된 Content-Type
    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("[HttpMediaTypeNotSupportedException] 지원하지 않는 Content-Type: {}", ex.getMessage());

        BaseErrorCode errorCode = GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .headers(headers)
                .body(ApiResponse.onFailure(errorCode));
    }

    // 잘못된 Http 메소드
    @Override
    protected @Nullable ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("[HttpRequestMethodNotSupportedException] 지원하지 않는 HTTP Method: {}", ex.getMessage());

        BaseErrorCode errorCode = GeneralErrorCode.METHOD_NOT_ALLOWED;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .headers(headers)
                .body(ApiResponse.onFailure(errorCode));
    }

    // 타입 불일치
    @Override
    protected @Nullable ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex instanceof MethodArgumentTypeMismatchException matEx) {
            Object value = matEx.getValue();
            String valueType = value != null ? value.getClass().getSimpleName() : "null";
            log.warn("[MethodArgumentTypeMismatchException] 타입 불일치: parameter={}, valueType={}", matEx.getName(), valueType);
        } else {
            log.warn("[TypeMismatchException] 타입 불일치: property={}", ex.getPropertyName());
        }

        BaseErrorCode errorCode = GeneralErrorCode.BAD_REQUEST;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .headers(headers)
                .body(ApiResponse.onFailure(errorCode));
    }

    // 존재하지 않는 엔드포인트
    @Override
    protected @Nullable ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("[NoResourceFoundException] 존재하지 않는 엔드포인트: {}", ex.getMessage());

        BaseErrorCode errorCode = GeneralErrorCode.NOT_FOUND;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .headers(headers)
                .body(ApiResponse.onFailure(errorCode));
    }

    // 컨트롤러 파라미터 검증 (클래스 레벨 @Validated 없을 때 스프링 기본 메소드 검증)
    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        // 반환값 검증 실패
        if (ex.isForReturnValue()) {
            log.error("[HandlerMethodValidationException] 컨트롤러 반환값 검증 실패: {}", ex.getMessage(), ex);
            BaseErrorCode errorCode = GeneralErrorCode.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(errorCode.getHttpStatus())
                    .headers(headers)
                    .body(ApiResponse.onFailure(errorCode));
        }

        // 입력값 검증 실패
        log.warn("[HandlerMethodValidationException] 컨트롤러 파라미터 검증 실패: {}", ex.getMessage());
        Map<String, String> errors = new HashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            if (result instanceof ParameterErrors parameterErrors) {
                parameterErrors.getFieldErrors().forEach(error ->
                        errors.put(error.getField(), error.getDefaultMessage()));
            } else {
                String parameterName = result.getMethodParameter().getParameterName();
                result.getResolvableErrors().forEach(error ->
                        errors.put(parameterName, error.getDefaultMessage()));
            }
        });

        BaseErrorCode errorCode = GeneralErrorCode.BAD_REQUEST;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .headers(headers)
                .body(ApiResponse.onFailure(errorCode, errors));
    }

    // 오버라이드 되지 않은 나머지 ResponseEntityExceptionHandler 예외
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is4xxClientError()) {
            log.warn("[{}] {}", ex.getClass().getSimpleName(), statusCode);
        } else {
            log.error("[{}] {} - {}", ex.getClass().getSimpleName(), statusCode, ex.getMessage(), ex);
        }

        ApiResponse<Void> response = new ApiResponse<>(
                false,
                "UNHANDLED_" + ex.getClass().getSimpleName(),
                "요청을 처리할 수 없습니다.",
                null
        );

        return super.handleExceptionInternal(ex, response, headers, statusCode, request);
    }

    // DB 상세정보 노출 없이 원인 예외가 어떤 종류인지만
    private static String causeDetail(Throwable cause) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(cause);
        if (root instanceof SQLException sqlEx) {
            return "sqlState=%s, vendorCode=%d".formatted(sqlEx.getSQLState(), sqlEx.getErrorCode());
        }
        return root.getClass().getSimpleName();
    }

}
