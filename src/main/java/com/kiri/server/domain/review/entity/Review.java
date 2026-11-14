package com.kiri.server.domain.review.entity;

import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "review",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_review_schedule", columnNames = {"schedule_id", "schedule_participant_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    private Schedule schedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_participant_id")
    private ScheduleParticipant scheduleParticipant;

    @Column(name = "content", length = 500)
    private String content;

    @Column(name = "star_rating")
    private Integer starRating;

    @Column(name = "is_rejoin_intended")
    @Builder.Default
    private Boolean isRejoinIntended = false;

    public void update(String content, Integer starRating, Boolean isRejoinIntended) {
        this.content = content;
        this.starRating = starRating;
        this.isRejoinIntended = isRejoinIntended;
    }
}
