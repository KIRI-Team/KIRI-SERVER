package com.kiri.server.domain.schedule.dto;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

public class ScheduleReqDTO {

    @Builder
    public record CalendarCondition(
            @Parameter(description = "조회 연도", example = "2026")
            @NotNull(message = "year는 필수입니다.")
            @Min(value = 2000, message = "year는 2000 이상이어야 합니다.")
            @Max(value = 2100, message = "year는 2100 이하여야 합니다.")
            Integer year,

            @Parameter(description = "조회 월(1~12)", example = "10")
            @NotNull(message = "month는 필수입니다.")
            @Min(value = 1, message = "month는 1 이상 12 이하여야 합니다.")
            @Max(value = 12, message = "month는 1 이상 12 이하여야 합니다.")
            Integer month
    ) {
    }
}
