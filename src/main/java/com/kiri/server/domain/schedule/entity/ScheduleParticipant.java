package com.kiri.server.domain.schedule.entity;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
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
}
