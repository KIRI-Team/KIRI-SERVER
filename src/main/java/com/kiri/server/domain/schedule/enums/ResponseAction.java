package com.kiri.server.domain.schedule.enums;

public enum ResponseAction {
    ACCEPT(ParticipantStatus.ACCEPTED),
    DECLINE(ParticipantStatus.DECLINED);

    private final ParticipantStatus target;

    ResponseAction(ParticipantStatus target) {
        this.target = target;
    }

    public ParticipantStatus toParticipantStatus() {
        return target;
    }
}
