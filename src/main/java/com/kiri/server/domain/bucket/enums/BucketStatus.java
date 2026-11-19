package com.kiri.server.domain.bucket.enums;

import java.util.List;

public enum BucketStatus {
    SAVED,
    SCHEDULED,
    COMPLETED;

    public static final List<BucketStatus> ACTIVE_STATUSES = List.of(SAVED, SCHEDULED);
}
