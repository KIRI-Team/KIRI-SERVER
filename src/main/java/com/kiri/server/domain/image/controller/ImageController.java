package com.kiri.server.domain.image.controller;

import com.kiri.server.domain.image.controller.docs.ImageControllerDocs;
import com.kiri.server.domain.image.dto.ImageReqDTO;
import com.kiri.server.domain.image.dto.ImageResDTO;
import com.kiri.server.domain.image.exception.code.ImageSuccessCode;
import com.kiri.server.domain.image.service.ImageService;
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
@RequestMapping("/api/v1/images")
public class ImageController implements ImageControllerDocs {
    private final ImageService imageService;

    @PostMapping("/presigned-url")
    public ApiResponse<ImageResDTO.PresignedUrl> issuePresignedUrl(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ImageReqDTO.PresignedUrl dto
    ) {
        BaseSuccessCode code = ImageSuccessCode.PRESIGNED_URL_ISSUED;
        return ApiResponse.onSuccess(code, imageService.issuePresignedUrl(memberId, dto));
    }
}
