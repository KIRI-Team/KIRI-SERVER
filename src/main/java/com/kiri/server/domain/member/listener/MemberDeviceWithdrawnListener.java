package com.kiri.server.domain.member.listener;

import com.kiri.server.domain.member.event.MemberWithdrawnEvent;
import com.kiri.server.domain.member.repository.MemberDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberDeviceWithdrawnListener {

    private final MemberDeviceRepository memberDeviceRepository;

    @EventListener
    public void handle(MemberWithdrawnEvent event) {
        Long memberId = event.memberId();
        memberDeviceRepository.deleteAllByMemberId(memberId);
    }
}
