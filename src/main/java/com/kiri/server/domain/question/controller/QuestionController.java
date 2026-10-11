package com.kiri.server.domain.question.controller;

import com.kiri.server.domain.question.controller.docs.QuestionControllerDocs;
import com.kiri.server.domain.question.dto.QuestionReqDTO;
import com.kiri.server.domain.question.dto.QuestionResDTO;
import com.kiri.server.domain.question.exception.code.QuestionSuccessCode;
import com.kiri.server.domain.question.service.QuestionService;
import com.kiri.server.global.apiPayload.ApiResponse;
import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    @PutMapping("/{questionId}/answers/me")
    public ApiResponse<Void> writeAnswer(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long questionId,
            @RequestBody @Valid QuestionReqDTO.WriteAnswer request
    ) {
        questionService.writeAnswer(memberId, questionId, request);
        BaseSuccessCode code = QuestionSuccessCode.WRITE_ANSWER;
        return ApiResponse.onSuccess(code);
    }

    @GetMapping("/{questionId}/answers")
    public ApiResponse<QuestionResDTO.FamilyAnswers> getFamilyAnswers(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long questionId
    ) {
        BaseSuccessCode code = QuestionSuccessCode.FAMILY_ANSWERS;
        return ApiResponse.onSuccess(code, questionService.getFamilyAnswers(memberId, questionId));
    }

    @PostMapping("/today/members/{memberId}/pokes")
    public ApiResponse<Void> poke(
            @AuthenticationPrincipal Long memberId,
            @PathVariable("memberId") Long targetId
    ) {
        questionService.poke(memberId, targetId);
        BaseSuccessCode code = QuestionSuccessCode.POKE;
        return ApiResponse.onSuccess(code);
    }
}
