package com.kiri.server.domain.notification.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationPriority {

    P0(0),
    P1(1),
    P2(2),
    P3(3),
    P4(4),
    P5(5);

    private final int value;
}
