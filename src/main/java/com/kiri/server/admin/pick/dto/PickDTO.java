package com.kiri.server.admin.pick.dto;

import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.pick.enums.PickType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class PickDTO {

    // 노출 상태. EXPOSED만 실제로 앱에 보인다.
    public enum Exposure { EXPOSED, SCHEDULED, EXPIRED, INACTIVE, MISSION_NOT_ACTIVE }

    public record PickRequest(
            @NotNull(message = "대표 미션은 필수입니다.") Long missionId,
            @NotBlank(message = "추천 문구는 필수입니다.") @Size(max = 100, message = "추천 문구는 100자 이하입니다.") String headline,
            @Size(max = 500, message = "설명은 500자 이하입니다.") String description,
            @Size(max = 500) String imageUrl,
            Integer displayOrder,
            @NotNull(message = "노출 시작 시각은 필수입니다.") LocalDateTime startAt,
            @NotNull(message = "노출 종료 시각은 필수입니다.") LocalDateTime endAt,
            Boolean active,
            PickType pickType,
            @Size(max = 100) String partnerName
    ) {
    }

    public record PickItem(
            Long kiriPickId,
            Long missionId,
            String missionTitle,
            MissionStatus missionStatus,
            String placeName,
            String headline,
            String description,
            String imageUrl,
            int displayOrder,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean active,
            PickType pickType,
            String partnerName,
            Exposure exposure,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record OrderRequest(@NotNull List<Long> kiriPickIds) {
    }

    // 노출·클릭은 앱 계측이 붙기 전까지 null(미수집)이다. 0과 구분한다.
    // 완료·별점은 버킷 출처로 한 픽에만 귀속되므로 여러 픽에 중복 집계되지 않는다. 방문·매출이 아니다.
    public record PickStats(
            Long kiriPickId,
            Long impressions,
            Long clicks,
            long saves,
            long schedules,
            long completedSchedules,
            Double averageStarRating,
            Double rejoinIntentRate,
            long reviewCount
    ) {
    }
}
