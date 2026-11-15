package com.kiri.server.domain.notification.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
    QUESTION_REMINDER(NotificationPriority.P5),
    QUESTION_COMPLETE(NotificationPriority.P4),
    SCHEDULE(NotificationPriority.P1),
    SCHEDULE_INVITATION(NotificationPriority.P1),
    SCHEDULE_INVITATION_REMINDER(NotificationPriority.P1),
    SCHEDULE_RESPONSE(NotificationPriority.P1),
    SCHEDULE_CHANGED(NotificationPriority.P0),
    SCHEDULE_REMOVED(NotificationPriority.P0),
    SCHEDULE_RECONFIRM_REMINDER(NotificationPriority.P0),
    SCHEDULE_CANCELLED(NotificationPriority.P0),
    SCHEDULE_NOT_CONFIRMED(NotificationPriority.P1),
    SCHEDULE_D1_REMINDER(NotificationPriority.P2),
    SCHEDULE_D0_REMINDER(NotificationPriority.P2),
    POKE(NotificationPriority.P3),
    RECORD(NotificationPriority.P5),
    RECORD_CREATED(NotificationPriority.P4),
    WEEKLY_MISSION_COMPLETED(NotificationPriority.P4),
    MISSION(NotificationPriority.P5),
    MISSION_CHANGED(NotificationPriority.P4),
    EXTERNAL_EVENT_CANCELLED(NotificationPriority.P1);

    private final NotificationPriority priority;
}
