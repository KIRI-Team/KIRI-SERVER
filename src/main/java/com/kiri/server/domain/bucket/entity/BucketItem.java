package com.kiri.server.domain.bucket.entity;

import com.kiri.server.domain.bucket.enums.BucketStatus;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.mission.entity.Mission;
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

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "bucket_item")
public class BucketItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bucket_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private BucketStatus status = BucketStatus.SAVED;

    @Column(name = "saved_at", nullable = false)
    private LocalDateTime savedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public void markScheduled() {
        this.status = BucketStatus.SCHEDULED;
    }

    public void markSaved() {
        this.status = BucketStatus.SAVED;
    }
}
