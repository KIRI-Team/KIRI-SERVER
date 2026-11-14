package com.kiri.server.domain.suggestion.controller;

import com.kiri.server.domain.suggestion.controller.docs.SuggestionControllerDocs;
import com.kiri.server.domain.suggestion.dto.SuggestionReqDTO;
import com.kiri.server.domain.suggestion.dto.SuggestionResDTO;
import com.kiri.server.domain.suggestion.exception.code.SuggestionSuccessCode;
import com.kiri.server.domain.suggestion.service.SuggestionService;
import com.kiri.server.global.apiPayload.ApiResponse;
import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/suggestions")
public class SuggestionController implements SuggestionControllerDocs {

    private final SuggestionService suggestionService;

    @Override
    @PostMapping
    public ApiResponse<SuggestionResDTO.Create> createSuggestion(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody SuggestionReqDTO.Create dto
    ) {
        BaseSuccessCode code = SuggestionSuccessCode.SUGGESTION_CREATED;
        return ApiResponse.onSuccess(code, suggestionService.createSuggestion(memberId, dto));
    }
}
