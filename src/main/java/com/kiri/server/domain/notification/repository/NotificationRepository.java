package com.kiri.server.domain.notification.repository;

import com.kiri.server.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    void deleteAllByMemberId(Long memberId);
}
