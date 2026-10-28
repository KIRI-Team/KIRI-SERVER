package com.kiri.server.domain.notification.listener;

import com.kiri.server.domain.member.event.MemberWithdrawnEvent;
import com.kiri.server.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationWithdrawnListener {

    private final NotificationRepository notificationRepository;

    @EventListener
    public void handle(MemberWithdrawnEvent event) {
        Long memberId = event.memberId();
        notificationRepository.deleteAllByMemberId(memberId);
    }
}
