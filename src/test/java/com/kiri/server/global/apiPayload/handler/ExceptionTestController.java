package com.kiri.server.global.apiPayload.handler;

import com.kiri.server.global.apiPayload.code.GeneralErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// GeneralExceptionAdviceTest용
@RestController
public class ExceptionTestController {

    record TestRequest(@NotBlank(message = "이름은 필수입니다.") String name) {
    }

    record MultiFieldRequest(
            @NotBlank(message = "이름은 필수입니다.") String name,
            @Email(message = "이메일 형식이 아닙니다.") String email
    ) {
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = AlwaysInvalidValidator.class)
    @interface AlwaysInvalid {
        String message() default "객체 검증에 실패했습니다.";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static class AlwaysInvalidValidator implements ConstraintValidator<AlwaysInvalid, Object> {
        @Override
        public boolean isValid(Object value, ConstraintValidatorContext context) {
            return false;
        }
    }

    @AlwaysInvalid
    record GlobalErrorRequest(String name) {
    }

    @GetMapping("/test/project-exception")
    void projectException() {
        throw new ProjectException(GeneralErrorCode.FORBIDDEN);
    }

    @PostMapping("/test/valid")
    void valid(@Valid @RequestBody TestRequest request) {
    }

    @GetMapping("/test/model-attribute")
    void modelAttribute(@Valid @ModelAttribute TestRequest request) {
    }

    @PostMapping("/test/global-error")
    void globalError(@Valid @RequestBody GlobalErrorRequest request) {
    }

    @GetMapping("/test/param")
    void param(@RequestParam @Min(value = 1, message = "1 이상이어야 합니다.") Integer page) {
    }

    @PostMapping("/test/method-validation")
    void methodValidation(@RequestParam @Min(value = 1, message = "1 이상이어야 합니다.") Integer page,
                          @Valid @RequestBody MultiFieldRequest request) {
    }

    @GetMapping("/test/return-value")
    @NotNull
    String returnValue() {
        return null;
    }

    @PostMapping("/test/method-validation/global-error")
    void methodValidationGlobalError(@RequestParam @Min(value = 1, message = "1 이상이어야 합니다.") Integer page,
                                     @Valid @RequestBody GlobalErrorRequest request) {
    }

    @GetMapping("/test/header")
    void header(@RequestHeader("X-Test") String header) {
    }

    @GetMapping("/test/data-integrity")
    void dataIntegrity() {
        throw new DataIntegrityViolationException("unique 제약 위반");
    }

    @GetMapping("/test/unexpected")
    void unexpected() {
        throw new IllegalStateException("예상치 못한 에러");
    }
}
