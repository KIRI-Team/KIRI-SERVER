package com.kiri.server.domain.notification.controller;

import com.kiri.server.domain.notification.controller.docs.NotificationControllerDocs;
import com.kiri.server.domain.notification.dto.NotificationResDTO;
import com.kiri.server.domain.notification.enums.NotificationCategory;
import com.kiri.server.domain.notification.exception.code.NotificationSuccessCode;
import com.kiri.server.domain.notification.service.NotificationInboxService;
import com.kiri.server.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationControllerDocs {

    private final NotificationInboxService notificationInboxService;

    @Override
    @GetMapping
    public ApiResponse<Page<NotificationResDTO.NotificationPreview>> getNotifications(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) NotificationCategory category,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.onSuccess(NotificationSuccessCode.GET_NOTIFICATION_LIST, notificationInboxService.getNotifications(memberId, category, pageable));
    }

    @Override
    @GetMapping("/unread-count")
    public ApiResponse<NotificationResDTO.UnreadCount> getUnreadCount(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(NotificationSuccessCode.GET_UNREAD_COUNT, notificationInboxService.getUnreadCount(memberId));
    }

    @Override
    @PatchMapping("/read-all")
    public ApiResponse<NotificationResDTO.ReadAllResult> markAllNotificationsAsRead(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(NotificationSuccessCode.READ_ALL_NOTIFICATIONS, notificationInboxService.markAllAsRead(memberId));
    }

    @Override
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markNotificationAsRead(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long notificationId
    ) {
        notificationInboxService.markAsRead(memberId, notificationId);
        return ApiResponse.onSuccess(NotificationSuccessCode.READ_NOTIFICATION);
    }
}
