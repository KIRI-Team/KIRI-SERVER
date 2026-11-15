package com.kiri.server.domain.notification.dto;

import com.kiri.server.domain.notification.enums.NotificationType;
import lombok.Builder;

import java.time.LocalDate;
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
}
