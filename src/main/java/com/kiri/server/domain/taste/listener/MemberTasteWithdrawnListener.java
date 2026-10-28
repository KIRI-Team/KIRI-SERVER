package com.kiri.server.domain.taste.listener;

import com.kiri.server.domain.member.event.MemberWithdrawnEvent;
import com.kiri.server.domain.taste.repository.MemberTasteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberTasteWithdrawnListener {

    private final MemberTasteRepository memberTasteRepository;

    @EventListener
    public void handle(MemberWithdrawnEvent event) {
        Long memberId = event.memberId();
        memberTasteRepository.deleteAllByMemberId(memberId);
    }
}
