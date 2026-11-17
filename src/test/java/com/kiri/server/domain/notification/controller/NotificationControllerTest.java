package com.kiri.server.domain.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiri.server.domain.notification.dto.NotificationResDTO;
import com.kiri.server.domain.notification.enums.NotificationType;
import com.kiri.server.domain.notification.exception.NotificationException;
import com.kiri.server.domain.notification.exception.code.NotificationErrorCode;
import com.kiri.server.domain.notification.exception.code.NotificationSuccessCode;
import com.kiri.server.domain.notification.enums.NotificationCategory;
import com.kiri.server.domain.notification.service.NotificationInboxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(NotificationControllerTest.TestConfig.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NotificationInboxService notificationService;

    @TestConfiguration
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private UsernamePasswordAuthenticationToken authenticatedMember() {
        return new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    @DisplayName("알림 목록 조회 성공")
    void getNotificationsSuccess() throws Exception {
        NotificationResDTO.NotificationPreview preview =
                NotificationResDTO.NotificationPreview.builder()
                        .notificationId(10L)
                        .title("알림 제목")
                        .body("알림 내용")
                        .notificationType(NotificationType.QUESTION_REMINDER)
                        .isRead(false)
                        .referenceId(100L)
                        .createdAt(LocalDateTime.of(2026, 11, 15, 10, 0))
                        .build();

        Page<NotificationResDTO.NotificationPreview> response = new PageImpl<>(List.of(preview), PageRequest.of(0, 20), 1);

        given(notificationService.getNotifications(1L, null, PageRequest.of(0, 20))).willReturn(response);

        mockMvc.perform(get("/api/v1/notifications")
                        .with(authentication(authenticatedMember()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code")
                        .value(NotificationSuccessCode.GET_NOTIFICATION_LIST.getCode()))
                .andExpect(jsonPath("$.message")
                        .value(NotificationSuccessCode.GET_NOTIFICATION_LIST.getMessage()))
                .andExpect(jsonPath("$.result.content[0].notificationId").value(10))
                .andExpect(jsonPath("$.result.content[0].title").value("알림 제목"))
                .andExpect(jsonPath("$.result.content[0].body").value("알림 내용"))
                .andExpect(jsonPath("$.result.content[0].notificationType").value("QUESTION_REMINDER"))
                .andExpect(jsonPath("$.result.content[0].isRead").value(false))
                .andExpect(jsonPath("$.result.totalElements").value(1));

        verify(notificationService).getNotifications(1L, null, PageRequest.of(0, 20));
    }

    @Test
    @DisplayName("알림 목록 조회 시 페이지 정보 전달 성공")
    void getNotificationsWithPageable() throws Exception {
        Page<NotificationResDTO.NotificationPreview> response = new PageImpl<>(List.of(), PageRequest.of(1, 10), 0);

        given(notificationService.getNotifications(1L, null, PageRequest.of(1, 10))).willReturn(response);

        mockMvc.perform(get("/api/v1/notifications")
                        .param("page", "1")
                        .param("size", "10")
                        .with(authentication(authenticatedMember()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.number").value(1))
                .andExpect(jsonPath("$.result.size").value(10))
                .andExpect(jsonPath("$.result.totalElements").value(0));

        verify(notificationService).getNotifications(1L, null, PageRequest.of(1, 10));
    }

    @Test
    @DisplayName("알림 읽음 처리 성공")
    void markNotificationAsReadSuccess() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", 10L)
                        .with(authentication(authenticatedMember()))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code")
                        .value(NotificationSuccessCode.READ_NOTIFICATION.getCode()))
                .andExpect(jsonPath("$.message")
                        .value(NotificationSuccessCode.READ_NOTIFICATION.getMessage()));

        verify(notificationService).markAsRead(1L, 10L);
    }

    @Test
    @DisplayName("존재하지 않는 알림을 읽음 처리하면 실패")
    void markNotificationAsReadNotFound() throws Exception {
        org.mockito.BDDMockito.willThrow(new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND)).given(notificationService).markAsRead(1L, 10L);

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", 10L)
                        .with(authentication(authenticatedMember()))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code")
                        .value(NotificationErrorCode.NOTIFICATION_NOT_FOUND.getCode()));

        verify(notificationService).markAsRead(1L, 10L);
    }

    @Test
    @DisplayName("탭 분류를 지정해 알림 목록을 조회한다")
    void getNotificationsWithCategory() throws Exception {
        given(notificationService.getNotifications(1L, NotificationCategory.SCHEDULE, PageRequest.of(0, 20)))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/notifications")
                        .param("category", "SCHEDULE")
                        .with(authentication(authenticatedMember())))
                .andExpect(status().isOk());

        verify(notificationService).getNotifications(1L, NotificationCategory.SCHEDULE, PageRequest.of(0, 20));
    }

    @Test
    @DisplayName("읽지 않은 알림 수를 조회한다")
    void getUnreadCount() throws Exception {
        given(notificationService.getUnreadCount(1L)).willReturn(new NotificationResDTO.UnreadCount(120, "99+"));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .with(authentication(authenticatedMember())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(NotificationSuccessCode.GET_UNREAD_COUNT.getCode()))
                .andExpect(jsonPath("$.result.unreadCount").value(120))
                .andExpect(jsonPath("$.result.badge").value("99+"));
    }

    @Test
    @DisplayName("모든 알림을 읽음 처리한다")
    void markAllAsRead() throws Exception {
        given(notificationService.markAllAsRead(1L)).willReturn(new NotificationResDTO.ReadAllResult(3));

        mockMvc.perform(patch("/api/v1/notifications/read-all")
                        .with(authentication(authenticatedMember()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(NotificationSuccessCode.READ_ALL_NOTIFICATIONS.getCode()))
                .andExpect(jsonPath("$.result.readCount").value(3));
    }
}
