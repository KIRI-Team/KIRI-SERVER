package com.kiri.server.domain.notification.controller;

import com.kiri.server.domain.notification.controller.docs.NotificationControllerDocs;
import com.kiri.server.domain.notification.dto.NotificationResDTO;
import com.kiri.server.domain.notification.exception.code.NotificationSuccessCode;
import com.kiri.server.domain.notification.service.NotificationService;
import com.kiri.server.global.apiPayload.ApiResponse;
import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
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

    private final NotificationService notificationService;

    @Override
    @GetMapping
    public ApiResponse<Page<NotificationResDTO.NotificationPreview>> getNotifications(
            @AuthenticationPrincipal Long memberId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        BaseSuccessCode code = NotificationSuccessCode.GET_NOTIFICATION_LIST;
        return ApiResponse.onSuccess(code, notificationService.getNotifications(memberId, pageable));
    }

    @Override
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markNotificationAsRead(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long notificationId
    ) {
        notificationService.markNotificationAsRead(memberId, notificationId);
        BaseSuccessCode code = NotificationSuccessCode.READ_NOTIFICATION;
        return ApiResponse.onSuccess(code);
    }
}
