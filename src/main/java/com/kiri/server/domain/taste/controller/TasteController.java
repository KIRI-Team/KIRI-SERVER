package com.kiri.server.domain.taste.controller;

import com.kiri.server.domain.taste.controller.docs.TasteControllerDocs;
import com.kiri.server.domain.taste.dto.TasteReqDTO;
import com.kiri.server.domain.taste.dto.TasteResDTO;
import com.kiri.server.domain.taste.exception.code.TasteSuccessCode;
import com.kiri.server.domain.taste.service.TasteService;
import com.kiri.server.global.apiPayload.ApiResponse;
import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members/taste")
public class TasteController implements TasteControllerDocs {

    private final TasteService tasteService;

    @GetMapping
    public ApiResponse<TasteResDTO.MyTaste> getMyTaste(
            @AuthenticationPrincipal Long memberId
    ) {
        BaseSuccessCode code = TasteSuccessCode.GET_SUCCESS;
        return ApiResponse.onSuccess(code, tasteService.getMyTaste(memberId));
    }

    @PutMapping
    public ApiResponse<TasteResDTO.MyTaste> saveMyTaste(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody TasteReqDTO.Save dto
    ) {
        BaseSuccessCode code = TasteSuccessCode.SAVE_SUCCESS;
        return ApiResponse.onSuccess(code, tasteService.saveMyTaste(memberId, dto));
    }
}
