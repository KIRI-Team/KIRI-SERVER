package com.kiri.server.global.apiPayload.handler;

import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// GeneralExceptionAdviceTest용
// ConstraintViolationException 검증
// ExceptionTestController만으로 HandlerMethodValidationException, ConstraintViolationException 동시 검증 불가능
// 실제 컨트롤러에는 @Validated를 붙이지 않음 (Spring 6.1+ 내장 검증 사용)
@Validated
@RestController
public class ValidatedTestController {

    @GetMapping("/test/validated-param")
    void validatedParam(@RequestParam @Min(value = 1, message = "1 이상이어야 합니다.") Integer page) {}
}
