package com.kiri.server.domain.term.listener;

import com.kiri.server.domain.member.event.MemberWithdrawnEvent;
import com.kiri.server.domain.term.repository.MemberTermRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberTermWithdrawnListener {

    private final MemberTermRepository memberTermRepository;

    @Order(4)
    @EventListener
    public void handle(MemberWithdrawnEvent event) {
        Long memberId = event.memberId();
        memberTermRepository.deleteAllByMemberId(memberId);
    }
}
