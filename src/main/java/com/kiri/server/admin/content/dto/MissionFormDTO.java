package com.kiri.server.admin.content.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.mission.enums.ItemType;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.mission.enums.WalkingLevel;
import com.kiri.server.domain.place.enums.PlaceType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// 검수 승인과 수동 등록이 같이 쓰는 미션 입력 폼
public class MissionFormDTO {

    @Builder(toBuilder = true)
    public record MissionForm(
            String title,
            String description,
            Category category,
            MissionType missionType,
            IndoorOutdoor indoorOutdoor,
            LocalDate startDate,
            LocalDate endDate,
            @JsonFormat(pattern = "HH:mm") LocalTime openingTime,
            @JsonFormat(pattern = "HH:mm") LocalTime closingTime,
            Integer price,
            Integer durationMinMinutes,
            Integer durationMaxMinutes,
            WalkingLevel walkingLevel,
            String imageUrl,
            String imageSource,
            String reservationUrl,
            PlaceForm place,
            List<ChecklistForm> checklist
    ) {
        public MissionForm {
            checklist = checklist == null ? List.of() : List.copyOf(checklist);
        }
    }

    // placeId가 있으면 기존 장소를 쓰고 나머지 값으로 그 장소를 수정한다. 없으면 새 장소를 만든다.
    @Builder(toBuilder = true)
    public record PlaceForm(
            Long placeId,
            String name,
            PlaceType placeType,
            String regionCode,
            BigDecimal latitude,
            BigDecimal longitude,
            String address,
            @JsonFormat(pattern = "HH:mm") LocalTime openingTime,
            @JsonFormat(pattern = "HH:mm") LocalTime closingTime,
            String imageUrl,
            String imageSource
    ) {
    }

    public record ChecklistForm(ItemType itemType, String title, String description) {
    }

    public record FieldError(String field, String message) {
    }

    public record ValidationResult(boolean valid, List<FieldError> errors) {
    }
}
