package com.kiri.server.domain.schedule.entity;

import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import com.kiri.server.domain.schedule.enums.ScheduleChangeField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleParticipantTest {

    private ScheduleParticipant accepted() {
        return ScheduleParticipant.builder().status(ParticipantStatus.ACCEPTED).build();
    }

    @Test
    @DisplayName("재확인 전에 일정이 또 바뀌면 바뀐 항목을 누적한다.")
    void markReconfirmRequired_accumulates() {
        ScheduleParticipant participant = accepted();

        participant.markReconfirmRequired(Set.of(ScheduleChangeField.DATE));
        participant.markReconfirmRequired(Set.of(ScheduleChangeField.TIME));

        assertThat(participant.getReconfirmRequired()).isTrue();
        assertThat(participant.getChangedFields())
                .containsExactly(ScheduleChangeField.DATE, ScheduleChangeField.TIME);
    }

    @Test
    @DisplayName("응답하면 바뀐 항목을 비우고, 이후 다시 바뀌면 새로 바뀐 항목만 남긴다.")
    void respond_clearsChangedFields() {
        ScheduleParticipant participant = accepted();
        participant.markReconfirmRequired(Set.of(ScheduleChangeField.DATE));

        participant.respond(ParticipantStatus.ACCEPTED, LocalDateTime.of(2026, 11, 1, 10, 0));

        assertThat(participant.getReconfirmRequired()).isFalse();
        assertThat(participant.getChangedFields()).isEmpty();

        participant.markReconfirmRequired(Set.of(ScheduleChangeField.PARTICIPANTS));

        assertThat(participant.getChangedFields()).containsExactly(ScheduleChangeField.PARTICIPANTS);
    }
}
