package com.kiri.server.domain.schedule.listener;

import com.kiri.server.domain.member.event.MemberWithdrawnEvent;
import com.kiri.server.domain.schedule.service.ScheduleCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduleWithdrawnListener {

    private final ScheduleCommandService scheduleCommandService;

    @EventListener
    public void handle(MemberWithdrawnEvent event) {
        scheduleCommandService.cleanUpWithdrawnMember(event.memberId());
    }
}
