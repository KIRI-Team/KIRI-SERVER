package com.kiri.server.domain.mission.enums;

public enum MissionStatus {
    ACTIVE,
    ENDED,
    CANCELLED,
    HIDDEN,
    CHECK_REQUIRED,
    // 관리자가 작성 중인 수동 등록 미션. 앱에 노출하지 않는다.
    DRAFT
}
