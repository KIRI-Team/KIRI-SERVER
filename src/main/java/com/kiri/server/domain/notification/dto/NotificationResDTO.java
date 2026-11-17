package com.kiri.server.domain.notification.dto;

import com.kiri.server.domain.notification.enums.NotificationCategory;
import com.kiri.server.domain.notification.enums.NotificationTargetType;
import com.kiri.server.domain.notification.enums.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class NotificationResDTO {

    @Builder
    public record NotificationProcessingTarget(
            Long notificationId,
            Long memberId,
            String title,
            String body,
            NotificationType notificationType,
            LocalDate referenceDate,
            UUID leaseId
    ) {}


    @Builder
    public record NotificationPreview(
            Long notificationId,
            String title,
            String body,
            @Schema(description = "세부 알림 종류")
            NotificationType notificationType,
            @Schema(description = "알림함 탭 분류 (SCHEDULE, POKE, RECORD, MISSION, QUESTION)")
            NotificationCategory category,
            @Schema(description = "알림을 눌렀을 때 이동할 화면. referenceId가 무엇을 가리키는지도 이 값으로 정해진다.")
            NotificationTargetType targetType,
            Boolean isRead,
            @Schema(description = "이동 대상 ID (targetType에 따라 질문·미션·일정 등의 ID)")
            Long referenceId,
            @Schema(description = "이동 대상 날짜 (질문 날짜, 주간 미션 시작일, 일정 날짜 등). 없으면 null")
            LocalDate referenceDate,
            LocalDateTime createdAt
    ) {}

    @Builder
    public record UnreadCount(
            @Schema(description = "최근 90일 안의 읽지 않은 알림 수", example = "120")
            long unreadCount,
            @Schema(description = "헤더 배지 표시값. 0개면 null, 100개 이상이면 \"99+\"", example = "99+", nullable = true)
            String badge
    ) {}

    @Builder
    public record ReadAllResult(
            @Schema(description = "이번 요청으로 읽음 처리된 알림 수", example = "5")
            int readCount
    ) {}
}
