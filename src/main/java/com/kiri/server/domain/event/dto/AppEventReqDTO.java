package com.kiri.server.domain.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class AppEventReqDTO {

    public record Batch(
            @NotEmpty(message = "이벤트는 1건 이상이어야 합니다.")
            @Size(max = 100, message = "이벤트는 한 번에 100건까지 보낼 수 있습니다.")
            List<@Valid Event> events
    ) {
    }

    public record Event(
            @Schema(description = "클라이언트가 만든 고유 ID(UUID). 같은 ID로 다시 보내면 무시합니다.")
            @NotBlank(message = "eventId는 필수입니다.")
            @Size(max = 64, message = "eventId는 64자 이하입니다.")
            String eventId,

            @Schema(description = "이벤트 이름. 예: mission_card_click")
            @NotBlank(message = "eventName은 필수입니다.")
            @Pattern(regexp = "^[a-z0-9_]{1,50}$", message = "eventName은 소문자·숫자·_ 50자 이하입니다.")
            String eventName,

            @Schema(description = "이벤트 속성(JSON 객체)")
            Map<String, Object> properties,

            @Schema(description = "ANDROID 또는 IOS")
            @Size(max = 20) String platform,

            @Size(max = 20) String appVersion,

            @Schema(description = "기기에서 이벤트가 발생한 시각(KST)")
            @NotNull(message = "occurredAt은 필수입니다.")
            LocalDateTime occurredAt
    ) {
    }
}
