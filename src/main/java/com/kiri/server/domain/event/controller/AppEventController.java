package com.kiri.server.domain.event.controller;

import com.kiri.server.domain.event.dto.AppEventReqDTO;
import com.kiri.server.domain.event.service.AppEventService;
import com.kiri.server.global.apiPayload.ApiResponse;
import com.kiri.server.global.apiPayload.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "앱 이벤트 수집 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/events")
public class AppEventController {

    private final AppEventService appEventService;

    @Operation(summary = "행동 이벤트 전송", description = """
            노출·클릭·화면 이탈 같은 앱 행동 이벤트를 최대 100건씩 모아 보냅니다.
            - eventId(UUID)가 같은 이벤트를 다시 보내면 무시합니다. 재전송해도 안전합니다.
            - 버킷 저장·일정·리뷰처럼 서버에 이미 기록되는 행동은 보내지 않아도 됩니다.
            - 응답의 inserted는 새로 저장된 건수입니다.""")
    @PostMapping
    public ApiResponse<Map<String, Integer>> record(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
                                                    @Valid @RequestBody AppEventReqDTO.Batch batch) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, Map.of("inserted", appEventService.record(memberId, batch)));
    }
}
