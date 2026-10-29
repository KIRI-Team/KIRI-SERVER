package com.kiri.server.domain.bucket.controller;

import com.kiri.server.domain.bucket.controller.docs.BucketControllerDocs;
import com.kiri.server.domain.bucket.dto.BucketResDTO;
import com.kiri.server.domain.bucket.exception.code.BucketSuccessCode;
import com.kiri.server.domain.bucket.service.BucketService;
import com.kiri.server.global.apiPayload.ApiResponse;
import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/missions/{missionId}/bucket")
public class BucketController implements BucketControllerDocs {

    private final BucketService bucketService;

    @PostMapping
    public ApiResponse<BucketResDTO.Saved> saveBucket(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long missionId
    ) {
        BaseSuccessCode code = BucketSuccessCode.SAVE_SUCCESS;
        return ApiResponse.onSuccess(code, bucketService.saveBucket(memberId, missionId));
    }
}
