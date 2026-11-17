package com.kiri.server.domain.notification.service;

import com.kiri.server.domain.notification.enums.NotificationCategory;
import com.kiri.server.domain.notification.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationInboxServiceTest {

    @Test
    @DisplayName("헤더 배지는 0개면 숨기고 100개 이상이면 99+로 표시한다")
    void badge() {
        assertThat(NotificationInboxService.toBadge(0)).isNull();
        assertThat(NotificationInboxService.toBadge(1)).isEqualTo("1");
        assertThat(NotificationInboxService.toBadge(99)).isEqualTo("99");
        assertThat(NotificationInboxService.toBadge(100)).isEqualTo("99+");
    }

    @Test
    @DisplayName("모든 알림 종류는 정확히 하나의 탭에 속한다")
    void everyTypeHasCategory() {
        long categorized = Arrays.stream(NotificationCategory.values()).mapToLong(c -> c.types().size()).sum();

        assertThat(categorized).isEqualTo(NotificationType.values().length);
        assertThat(NotificationCategory.POKE.types()).containsExactly(NotificationType.POKE);
        assertThat(NotificationCategory.MISSION.types()).containsExactlyInAnyOrder(NotificationType.MISSION, NotificationType.MISSION_CHANGED);
    }
}
