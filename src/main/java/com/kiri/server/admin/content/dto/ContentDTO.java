package com.kiri.server.admin.content.dto;

import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.LockedField;
import com.kiri.server.domain.mission.enums.MissionOrigin;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.place.enums.PlaceType;
import com.kiri.server.domain.question.enums.TasteType;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ContentDTO {

    // ===== 미션 =====

    public record MissionCondition(
            @Parameter(description = "공개 상태") MissionStatus status,
            @Parameter(description = "카테고리") Category category,
            @Parameter(description = "출처. EXTERNAL(외부 수집) / MANUAL(수동 등록)") MissionOrigin origin,
            @Parameter(description = "상시·기간형") MissionType missionType,
            @Parameter(description = "지역 코드 앞자리") String regionCode,
            @Parameter(description = "미션명 또는 장소명 부분 일치") String keyword,
            Integer page,
            Integer size
    ) {
    }

    public record MissionItem(
            Long missionId,
            String title,
            MissionStatus status,
            MissionOrigin origin,
            Category category,
            MissionType missionType,
            LocalDate startDate,
            LocalDate endDate,
            Long placeId,
            String placeName,
            String regionCode,
            String imageUrl,
            int lockedFieldCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record MissionDetail(
            Long missionId,
            MissionStatus status,
            MissionOrigin origin,
            List<LockedField> lockedFields,
            Long externalRecordId,
            String sourceUrl,
            String cancelReason,
            MissionFormDTO.MissionForm form,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record UpdateMissionRequest(MissionFormDTO.MissionForm form, String reason) {
    }

    public record ReasonRequest(String reason) {
    }

    // ===== 장소 =====

    public record PlaceCondition(
            @Parameter(description = "장소명 부분 일치") String keyword,
            @Parameter(description = "지역 코드 앞자리") String regionCode,
            Integer page,
            Integer size
    ) {
    }

    public record PlaceItem(
            Long placeId,
            String name,
            PlaceType placeType,
            String regionCode,
            BigDecimal latitude,
            BigDecimal longitude,
            String address,
            String imageUrl,
            long missionCount,
            List<LockedField> lockedFields
    ) {
    }

    // ===== 질문 =====

    public record QuestionCondition(
            @Parameter(description = "활성 여부") Boolean active,
            @Parameter(description = "질문 내용 부분 일치") String keyword,
            Integer page,
            Integer size
    ) {
    }

    public record QuestionRequest(
            @NotBlank(message = "질문 내용은 필수입니다.") @Size(max = 255, message = "질문은 255자 이하입니다.") String content,
            @Min(value = 1, message = "고정 일차는 1~7입니다.") @Max(value = 7, message = "고정 일차는 1~7입니다.") Integer fixedDay,
            TasteType tasteType
    ) {
    }

    public record QuestionItem(
            Long questionId,
            String content,
            boolean active,
            Integer fixedDay,
            TasteType tasteType,
            long assignedCount,
            LocalDateTime createdAt
    ) {
    }

    // ===== 이미지 =====

    public record ConfirmImageRequest(@NotBlank(message = "imageKey는 필수입니다.") String imageKey) {
    }

    public record ConfirmedImage(String imageKey, String imageUrl) {
    }
}
