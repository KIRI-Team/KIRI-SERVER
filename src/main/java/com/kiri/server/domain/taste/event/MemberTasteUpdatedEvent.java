package com.kiri.server.domain.taste.event;

public record MemberTasteUpdatedEvent(
        Long memberId,
        Long familyId
) {}
