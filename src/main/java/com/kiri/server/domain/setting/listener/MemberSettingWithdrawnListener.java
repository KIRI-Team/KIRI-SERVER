package com.kiri.server.domain.setting.listener;

import com.kiri.server.domain.member.event.MemberWithdrawnEvent;
import com.kiri.server.domain.setting.repository.MemberSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberSettingWithdrawnListener {

    private final MemberSettingRepository memberSettingRepository;

    @EventListener
    public void handle(MemberWithdrawnEvent event) {
        Long memberId = event.memberId();
        memberSettingRepository.deleteAllByMemberId(memberId);
    }
}
