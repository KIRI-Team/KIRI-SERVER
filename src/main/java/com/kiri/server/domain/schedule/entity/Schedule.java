package com.kiri.server.domain.schedule.entity;

import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "schedule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Schedule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bucket_item_id")
    private BucketItem bucketItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposer_id")
    private Member proposer;

    @Column(name = "schedule_date")
    private LocalDate scheduleDate;

    @Column(name = "schedule_time")
    private LocalTime scheduleTime;

    @Column(name = "request_message", length = 100)
    private String requestMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private ScheduleStatus status = ScheduleStatus.PENDING_RESPONSE;

    @Column(name = "response_deadline")
    private LocalDateTime responseDeadline;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public static Schedule create(Family family, BucketItem bucketItem, Member proposer,
                                  LocalDate scheduleDate, LocalTime scheduleTime,
                                  String requestMessage, LocalDateTime responseDeadline) {
        return Schedule.builder()
                .family(family)
                .bucketItem(bucketItem)
                .proposer(proposer)
                .scheduleDate(scheduleDate)
                .scheduleTime(scheduleTime)
                .requestMessage(requestMessage)
                .responseDeadline(responseDeadline)
                .status(ScheduleStatus.PENDING_RESPONSE)
                .build();
    }

    public boolean isResponseOpen(LocalDateTime now) {
        return (status == ScheduleStatus.PENDING_RESPONSE || status == ScheduleStatus.CONFIRMED)
                && responseDeadline != null && !now.isAfter(responseDeadline);
    }

    public void recalculateStatus(long acceptedCountExcludingProposer, LocalDateTime now) {
        if (status == ScheduleStatus.CANCELLED || status == ScheduleStatus.COMPLETED) {
            return;
        }
        if (acceptedCountExcludingProposer >= 1) {
            this.status = ScheduleStatus.CONFIRMED;
        } else if (responseDeadline != null && now.isAfter(responseDeadline)) {
            this.status = ScheduleStatus.NOT_CONFIRMED;
        } else {
            this.status = ScheduleStatus.PENDING_RESPONSE;
        }
    }

    public boolean isCancelable() {
        return status == ScheduleStatus.PENDING_RESPONSE || status == ScheduleStatus.CONFIRMED;
    }

    public void cancel() {
        this.status = ScheduleStatus.CANCELLED;
    }

    public boolean isProposer(Long memberId) {
        return proposer != null && proposer.getId().equals(memberId);
    }
}
