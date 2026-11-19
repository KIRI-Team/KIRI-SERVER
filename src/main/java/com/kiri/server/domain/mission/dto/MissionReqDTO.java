package com.kiri.server.domain.mission.dto;

import com.kiri.server.domain.mission.enums.Category;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

public class MissionReqDTO {

    @Builder
    public record FeedCondition(
            @Parameter(description = "카테고리", example = "EXHIBITION")
            Category category,

            @Parameter(description = "지역 코드. 2자리 시·도, 5자리 시·군·구", example = "11200")
            String regionCode,

            @Parameter(description = "거리(km). 1, 3, 5, 10", example = "3")
            Integer distance,

            @Parameter(description = "기준 위도. longitude와 함께 입력, 없으면 우리 집 위치 사용", example = "37.5446")
            Double latitude,

            @Parameter(description = "기준 경도. latitude와 함께 입력, 없으면 우리 집 위치 사용", example = "127.0557")
            Double longitude,

            @Parameter(description = "방문 날짜(yyyy-MM-dd)", example = "2026-10-18")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,

            @Parameter(description = "방문 시각(HH:mm)", example = "14:00")
            @DateTimeFormat(pattern = "HH:mm")
            LocalTime time,

            @Parameter(description = "무료 미션만 조회", example = "true")
            Boolean isFree,

            @Parameter(description = "최대 가격(원)", example = "20000")
            @PositiveOrZero(message = "maxPrice는 0 이상이어야 합니다.")
            Integer maxPrice,

            @Parameter(description = "이전 응답의 nextCursor. 첫 조회는 생략", example = "128")
            Long cursor,

            @Parameter(description = "조회 개수. 1~50, 기본 20", example = "20")
            @Min(value = 1, message = "size는 1 이상 50 이하여야 합니다.")
            @Max(value = 50, message = "size는 1 이상 50 이하여야 합니다.")
            Integer size
    ) {

        public boolean hasCoordinates() {
            return latitude != null && longitude != null;
        }

        public boolean freeOnly() {
            return Boolean.TRUE.equals(isFree);
        }
    }
}
