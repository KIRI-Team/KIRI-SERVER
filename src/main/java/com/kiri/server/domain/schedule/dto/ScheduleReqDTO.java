package com.kiri.server.domain.schedule.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

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

    @Builder
    public record Create(
            @Schema(description = "일정 날짜(yyyy-MM-dd)", example = "2026-11-15")
            @NotNull(message = "scheduleDate는 필수입니다.")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate scheduleDate,

            @Schema(description = "일정 시간(HH:mm)", example = "14:00")
            @NotNull(message = "scheduleTime은 필수입니다.")
            @DateTimeFormat(pattern = "HH:mm")
            LocalTime scheduleTime,

            @Schema(description = "초대할 가족 구성원 memberId 목록(제안자 제외)", example = "[2, 3]")
            @NotEmpty(message = "초대할 가족 구성원을 1명 이상 선택해 주세요.")
            List<Long> participantMemberIds,

            @Schema(description = "가족에게 한 줄 메시지", example = "이번 주말에 같이 가요!")
            @Size(max = 100, message = "메시지는 100자 이하로 입력해 주세요.")
            String requestMessage
    ) {
    }
}
