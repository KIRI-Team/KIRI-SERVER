package com.kiri.server.domain.notification.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static com.kiri.server.domain.notification.enums.NotificationPriority.*;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
    QUESTION_REMINDER(P5, NotificationCategory.QUESTION, NotificationTargetType.QUESTION),
    QUESTION_COMPLETE(P4, NotificationCategory.QUESTION, NotificationTargetType.FAMILY_ANSWERS),
    SCHEDULE(P1, NotificationCategory.SCHEDULE, NotificationTargetType.SCHEDULE),
    SCHEDULE_INVITATION(P1, NotificationCategory.SCHEDULE, NotificationTargetType.SCHEDULE_INVITATION),
    SCHEDULE_INVITATION_REMINDER(P1, NotificationCategory.SCHEDULE, NotificationTargetType.SCHEDULE_INVITATION),
    SCHEDULE_RESPONSE(P1, NotificationCategory.SCHEDULE, NotificationTargetType.SCHEDULE),
    SCHEDULE_CHANGED(P0, NotificationCategory.SCHEDULE, NotificationTargetType.SCHEDULE),
    SCHEDULE_REMOVED(P0, NotificationCategory.SCHEDULE, NotificationTargetType.SCHEDULE),
    SCHEDULE_RECONFIRM_REMINDER(P0, NotificationCategory.SCHEDULE, NotificationTargetType.SCHEDULE),
    SCHEDULE_CANCELLED(P0, NotificationCategory.SCHEDULE, NotificationTargetType.SCHEDULE),
    SCHEDULE_NOT_CONFIRMED(P1, NotificationCategory.SCHEDULE, NotificationTargetType.SCHEDULE),
    SCHEDULE_D1_REMINDER(P2, NotificationCategory.SCHEDULE, NotificationTargetType.SCHEDULE),
    SCHEDULE_D0_REMINDER(P2, NotificationCategory.SCHEDULE, NotificationTargetType.SCHEDULE),
    POKE(P3, NotificationCategory.POKE, NotificationTargetType.QUESTION),
    RECORD(P5, NotificationCategory.RECORD, NotificationTargetType.REVIEW_WRITE),
    RECORD_CREATED(P4, NotificationCategory.RECORD, NotificationTargetType.SCHEDULE_RECORD),
    WEEKLY_MISSION_COMPLETED(P4, NotificationCategory.RECORD, NotificationTargetType.WEEKLY_MISSION_RECORD),
    MISSION(P5, NotificationCategory.MISSION, NotificationTargetType.WEEKLY_MISSION),
    MISSION_CHANGED(P4, NotificationCategory.MISSION, NotificationTargetType.WEEKLY_MISSION),
    // 외부 행사 취소는 일정에 영향을 주는 안내라 일정 탭에 둔다. referenceId는 미션 ID다.
    EXTERNAL_EVENT_CANCELLED(P1, NotificationCategory.SCHEDULE, NotificationTargetType.MISSION);

    private final NotificationPriority priority;
    private final NotificationCategory category;
    private final NotificationTargetType targetType;
}
