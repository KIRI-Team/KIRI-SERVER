package com.kiri.server.domain.notification.entity;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.notification.enums.NotificationStatus;
import com.kiri.server.domain.notification.enums.NotificationType;
import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "notification"
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;

    @Column(name = "title", length = 100, nullable = false)
    private String title;

    @Column(name = "body", length = 255, nullable = false)
    private String body;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NotificationStatus status;

    @Column(name = "processing_at")
    private LocalDateTime processingAt;

    @Column(name = "processing_lease_id")
    private UUID processingLeaseId;
}