package com.kiri.server.domain.schedule.enums;

import java.util.List;

public enum ScheduleStatus {
    PENDING_RESPONSE,
    CONFIRMED,
    NOT_CONFIRMED,
    CANCELLED,
    COMPLETED;

    public static final List<ScheduleStatus> ACTIVE_STATUSES = List.of(PENDING_RESPONSE, CONFIRMED);
}
