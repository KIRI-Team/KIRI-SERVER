package com.kiri.server.domain.mission.enums;

// 운영자가 직접 고쳐서 외부 재수집이 덮어쓰지 않는 필드
public enum LockedField {
    // 미션
    TITLE,
    DESCRIPTION,
    CATEGORY,
    MISSION_TYPE,
    INDOOR_OUTDOOR,
    PERIOD,
    OPENING_TIME,
    CLOSING_TIME,
    PRICE,
    DURATION,
    WALKING_LEVEL,
    IMAGE,
    CHECKLIST,
    RESERVATION_URL,
    PLACE,
    // 장소
    NAME,
    PLACE_TYPE,
    REGION,
    COORDINATE,
    ADDRESS
}
