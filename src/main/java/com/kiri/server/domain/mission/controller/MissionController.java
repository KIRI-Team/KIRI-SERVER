package com.kiri.server.domain.mission.controller;

import com.kiri.server.domain.mission.controller.docs.MissionControllerDocs;
import com.kiri.server.domain.mission.dto.MissionReqDTO;
import com.kiri.server.domain.mission.dto.MissionResDTO;
import com.kiri.server.domain.mission.exception.code.MissionSuccessCode;
import com.kiri.server.domain.mission.service.MissionQueryService;
import com.kiri.server.global.apiPayload.ApiResponse;
import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/missions")
public class MissionController implements MissionControllerDocs {

    private final MissionQueryService missionQueryService;

    @GetMapping
    public ApiResponse<MissionResDTO.Feed> getFeed(
            @AuthenticationPrincipal Long memberId,
            @Valid @ModelAttribute MissionReqDTO.FeedCondition condition
    ) {
        BaseSuccessCode code = MissionSuccessCode.FEED_SUCCESS;
        return ApiResponse.onSuccess(code, missionQueryService.getFeed(memberId, condition));
    }

    @GetMapping("/{missionId}")
    public ApiResponse<MissionResDTO.Detail> getDetail(
            @PathVariable Long missionId
    ) {
        BaseSuccessCode code = MissionSuccessCode.DETAIL_SUCCESS;
        return ApiResponse.onSuccess(code, missionQueryService.getDetail(missionId));
    }
}
