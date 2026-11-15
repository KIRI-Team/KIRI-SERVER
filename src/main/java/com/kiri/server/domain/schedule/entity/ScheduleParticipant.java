package com.kiri.server.domain.schedule.entity;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import com.kiri.server.domain.schedule.enums.ScheduleChangeField;
import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Entity
@Table(
        name = "schedule_participant",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_schedule_participant", columnNames = {"schedule_id", "member_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ScheduleParticipant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_participant_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private ParticipantStatus status = ParticipantStatus.PENDING;

    @Column(name = "is_reconfirm_required", nullable = false)
    @Builder.Default
    private Boolean reconfirmRequired = false;

    @Column(name = "is_date_changed", nullable = false)
    @Builder.Default
    private Boolean dateChanged = false;

    @Column(name = "is_time_changed", nullable = false)
    @Builder.Default
    private Boolean timeChanged = false;

    @Column(name = "is_participants_changed", nullable = false)
    @Builder.Default
    private Boolean participantsChanged = false;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    public static ScheduleParticipant of(Schedule schedule, Member member, ParticipantStatus status,
                                         LocalDateTime respondedAt) {
        return ScheduleParticipant.builder()
                .schedule(schedule)
                .member(member)
                .status(status)
                .reconfirmRequired(false)
                .respondedAt(respondedAt)
                .build();
    }

    public void respond(ParticipantStatus status, LocalDateTime respondedAt) {
        this.status = status;
        this.reconfirmRequired = false;
        clearChangedFields();
        this.respondedAt = respondedAt;
    }

    // 재확인 전에 또 바뀌면 이전 항목에 누적
    public void markReconfirmRequired(Set<ScheduleChangeField> changedFields) {
        if (!Boolean.TRUE.equals(reconfirmRequired)) {
            clearChangedFields();
        }
        this.reconfirmRequired = true;
        this.dateChanged |= changedFields.contains(ScheduleChangeField.DATE);
        this.timeChanged |= changedFields.contains(ScheduleChangeField.TIME);
        this.participantsChanged |= changedFields.contains(ScheduleChangeField.PARTICIPANTS);
    }

    public List<ScheduleChangeField> getChangedFields() {
        List<ScheduleChangeField> fields = new ArrayList<>();
        if (Boolean.TRUE.equals(dateChanged)) {
            fields.add(ScheduleChangeField.DATE);
        }
        if (Boolean.TRUE.equals(timeChanged)) {
            fields.add(ScheduleChangeField.TIME);
        }
        if (Boolean.TRUE.equals(participantsChanged)) {
            fields.add(ScheduleChangeField.PARTICIPANTS);
        }
        return fields;
    }

    private void clearChangedFields() {
        this.dateChanged = false;
        this.timeChanged = false;
        this.participantsChanged = false;
    }
}
