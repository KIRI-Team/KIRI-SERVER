package com.kiri.server.domain.notification.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

/**
 * 알림함 상단 탭 분류. 전체 탭은 분류를 지정하지 않는 것으로 표현한다.
 */
@Getter
@RequiredArgsConstructor
public enum NotificationCategory {

    SCHEDULE("일정"),
    POKE("콕"),
    RECORD("기록"),
    MISSION("미션"),
    QUESTION("질문");

    private final String label;

    public List<NotificationType> types() {
        return Arrays.stream(NotificationType.values())
                .filter(type -> type.getCategory() == this)
                .toList();
    }
}
