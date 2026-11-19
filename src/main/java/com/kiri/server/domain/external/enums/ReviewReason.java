package com.kiri.server.domain.external.enums;

public enum ReviewReason {
    NO_LOCATION,
    CATEGORY_UNKNOWN,
    MISSING_REQUIRED,
    TITLE_INVALID,
    ACTIVITY_SHORTAGE,
    PLACE_AMBIGUOUS,
    DUPLICATE_SUSPECT,
    ORIGIN_UNAVAILABLE,
    EXCLUDED_TYPE,
    PERIOD_OVER,
    DUPLICATE,
    EXTERNAL_CANCELLED,
    // 관리자가 검수에서 제외했다. 재수집해도 다시 처리하지 않는다.
    ADMIN_EXCLUDED
}
