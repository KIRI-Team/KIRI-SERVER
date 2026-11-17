package com.kiri.server.domain.notification.service;

import com.kiri.server.domain.notification.converter.NotificationConverter;
import com.kiri.server.domain.notification.dto.NotificationResDTO;
import com.kiri.server.domain.notification.entity.Notification;
import com.kiri.server.domain.notification.enums.NotificationCategory;
import com.kiri.server.domain.notification.exception.NotificationException;
import com.kiri.server.domain.notification.exception.code.NotificationErrorCode;
import com.kiri.server.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 앱 알림함(조회·미읽음 수·읽음 처리)과 보관기간 정리를 담당한다.
 * 알림함에는 생성 후 90일 이내 알림만 보여주고, 그보다 오래된 알림은 정기 작업으로 삭제한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationInboxService {

    public static final int RETENTION_DAYS = 90;
    static final int MAX_BADGE_COUNT = 99;

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public Page<NotificationResDTO.NotificationPreview> getNotifications(Long memberId, NotificationCategory category, Pageable pageable) {
        LocalDateTime since = retentionStart();
        // 정렬은 생성시각·ID 내림차순으로 고정한다. 클라이언트가 보낸 sort는 무시한다.
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        Page<Notification> notifications = category == null
                ? notificationRepository.findInbox(memberId, since, page)
                : notificationRepository.findInboxByTypes(memberId, category.types(), since, page);

        return notifications.map(NotificationConverter::toNotificationPreview);
    }

    public NotificationResDTO.UnreadCount getUnreadCount(Long memberId) {
        long count = notificationRepository.countByMemberIdAndIsReadFalseAndCreatedAtGreaterThanEqual(memberId, retentionStart());

        return NotificationResDTO.UnreadCount.builder()
                .unreadCount(count)
                .badge(toBadge(count))
                .build();
    }

    @Transactional
    public void markAsRead(Long memberId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndMemberId(notificationId, memberId)
                .orElseThrow(() -> new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));

        notification.markAsRead();
    }

    /** 선택한 탭과 관계없이 회원의 모든 미읽음 알림을 읽음 처리한다. */
    @Transactional
    public NotificationResDTO.ReadAllResult markAllAsRead(Long memberId) {
        int readCount = notificationRepository.markAllAsRead(memberId);

        return NotificationResDTO.ReadAllResult.builder()
                .readCount(readCount)
                .build();
    }

    /** 보관기간이 지난 알림을 한 묶음 삭제하고 삭제한 개수를 돌려준다. */
    @Transactional
    public int deleteExpiredBatch(int batchSize) {
        return notificationRepository.deleteCreatedBefore(retentionStart(), batchSize);
    }

    static String toBadge(long count) {
        if (count <= 0) {
            return null;
        }
        return count > MAX_BADGE_COUNT ? MAX_BADGE_COUNT + "+" : String.valueOf(count);
    }

    private LocalDateTime retentionStart() {
        return LocalDateTime.now(clock).minusDays(RETENTION_DAYS);
    }
}
