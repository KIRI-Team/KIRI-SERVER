package com.kiri.server.domain.question.controller;

import com.kiri.server.domain.question.controller.docs.QuestionControllerDocs;
import com.kiri.server.domain.question.dto.QuestionResDTO;
import com.kiri.server.domain.question.exception.code.QuestionSuccessCode;
import com.kiri.server.domain.question.service.QuestionService;
import com.kiri.server.global.apiPayload.ApiResponse;
import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/questions")
public class QuestionController implements QuestionControllerDocs {

    private final QuestionService questionService;

    @GetMapping("/today")
    public ApiResponse<QuestionResDTO.TodayQuestion> getTodayQuestion(
            @AuthenticationPrincipal Long memberId
    ) {
        BaseSuccessCode code = QuestionSuccessCode.TODAY_QUESTION;
        return ApiResponse.onSuccess(code, questionService.getTodayQuestion(memberId));
    }
}
