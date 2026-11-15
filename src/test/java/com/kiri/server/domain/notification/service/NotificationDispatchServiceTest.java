package com.kiri.server.domain.notification.service;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.kiri.server.domain.member.entity.MemberDevice;
import com.kiri.server.domain.member.repository.MemberDeviceRepository;
import com.kiri.server.domain.notification.dto.NotificationResDTO;
import com.kiri.server.domain.notification.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTest {

    @Mock
    private FcmService fcmService;

    @Mock
    private MemberDeviceRepository memberDeviceRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private Clock clock;

    @InjectMocks
    private NotificationDispatchService notificationDispatchService;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(Instant.parse("2026-11-15T12:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.of("Asia/Seoul"));
    }

    private NotificationResDTO.NotificationProcessingTarget createTarget(NotificationType notificationType, LocalDate referenceDate) {
        return NotificationResDTO.NotificationProcessingTarget.builder()
                .notificationId(1L)
                .memberId(1L)
                .title("알림 제목")
                .body("알림 내용")
                .notificationType(notificationType)
                .referenceDate(referenceDate)
                .leaseId(UUID.randomUUID())
                .build();
    }

    private MemberDevice createDevice(String token) {
        MemberDevice device = mock(MemberDevice.class);
        given(device.getFcmToken()).willReturn(token);
        return device;
    }

    private void setFixedTime(String instant) {
        given(clock.instant()).willReturn(Instant.parse(instant));
    }

    @Test
    @DisplayName("정상적인 알림은 FCM 발송 후 SENT 처리한다")
    void dispatchPendingNotificationsSuccess() throws Exception {
        NotificationResDTO.NotificationProcessingTarget target = createTarget(NotificationType.SCHEDULE, null);

        MemberDevice device = createDevice("test-token");

        given(notificationService.claimPendingNotifications()).willReturn(List.of(target));

        given(memberDeviceRepository.findAllByMemberId(1L)).willReturn(List.of(device));

        notificationDispatchService.dispatchPendingNotifications();

        verify(fcmService).send("test-token", target.title(), target.body(), target.notificationType());

        verify(notificationService).markNotificationSent(target.notificationId(), target.leaseId());
    }

    @Test
    @DisplayName("질문 리마인더 날짜가 지나면 EXPIRED 처리한다")
    void dispatchPendingNotificationsExpiredQuestionReminder() throws Exception {
        NotificationResDTO.NotificationProcessingTarget target = createTarget(NotificationType.QUESTION_REMINDER, LocalDate.now().minusDays(1));

        given(notificationService.claimPendingNotifications()).willReturn(List.of(target));

        notificationDispatchService.dispatchPendingNotifications();

        verify(notificationService).expireNotification(target.notificationId(), target.leaseId());

        verify(memberDeviceRepository, never()).findAllByMemberId(anyLong());
        verify(fcmService, never()).send(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("등록된 디바이스가 없으면 SKIPPED 처리한다")
    void dispatchPendingNotificationsNoDevices() throws Exception {
        NotificationResDTO.NotificationProcessingTarget target = createTarget(NotificationType.SCHEDULE, null);

        given(notificationService.claimPendingNotifications()).willReturn(List.of(target));

        given(memberDeviceRepository.findAllByMemberId(1L)).willReturn(List.of());

        notificationDispatchService.dispatchPendingNotifications();

        verify(notificationService).markNotificationSkipped(target.notificationId(), target.leaseId());

        verify(fcmService, never()).send(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("유효하지 않은 FCM 토큰은 삭제하고 발송할 디바이스가 없으면 SKIPPED 처리한다")
    void dispatchPendingNotificationsUnregisteredToken() throws Exception {
        NotificationResDTO.NotificationProcessingTarget target = createTarget(NotificationType.SCHEDULE, null);

        MemberDevice device = createDevice("invalid-token");

        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        given(exception.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNREGISTERED);

        given(notificationService.claimPendingNotifications()).willReturn(List.of(target));

        given(memberDeviceRepository.findAllByMemberId(1L)).willReturn(List.of(device));

        given(fcmService.send(anyString(), anyString(), anyString(), any())).willThrow(exception);

        notificationDispatchService.dispatchPendingNotifications();

        verify(memberDeviceRepository).deleteByFcmToken("invalid-token");

        verify(notificationService).markNotificationSkipped(target.notificationId(), target.leaseId());

        verify(notificationService, never()).markNotificationFailed(anyLong(), any());
    }

    @Test
    @DisplayName("일시적인 FCM 오류가 발생하면 FAILED 처리한다")
    void dispatchPendingNotificationsFcmFailure() throws Exception {
        NotificationResDTO.NotificationProcessingTarget target = createTarget(NotificationType.SCHEDULE, null);

        MemberDevice device = createDevice("test-token");

        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        given(exception.getMessagingErrorCode()).willReturn(null);

        given(notificationService.claimPendingNotifications()).willReturn(List.of(target));

        given(memberDeviceRepository.findAllByMemberId(1L)).willReturn(List.of(device));

        given(fcmService.send(anyString(), anyString(), anyString(), any())).willThrow(exception);

        notificationDispatchService.dispatchPendingNotifications();

        verify(notificationService).markNotificationFailed(target.notificationId(), target.leaseId());

        verify(memberDeviceRepository, never()).deleteByFcmToken(anyString());
    }

    @Test
    @DisplayName("야간에 P2 알림이 발생하면 다음 날 오전 9시로 지연한다")
    void dispatchPendingNotifications_deferDuringNight() throws Exception {
        setFixedTime("2026-11-15T13:00:00Z");

        NotificationResDTO.NotificationProcessingTarget target = createTarget(NotificationType.SCHEDULE_D1_REMINDER, LocalDate.now());

        given(notificationService.claimPendingNotifications()).willReturn(List.of(target));

        notificationDispatchService.dispatchPendingNotifications();

        verify(notificationService).deferNotification(eq(target.notificationId()), eq(target.leaseId()), eq(LocalDateTime.of(2026, 11, 16, 9, 0)));

        verify(fcmService, never()).send(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("오전 9시 이전 P2 알림은 당일 오전 9시로 지연한다")
    void dispatchPendingNotifications_deferBeforeMorning() throws Exception {
        setFixedTime("2026-11-14T23:00:00Z");

        NotificationResDTO.NotificationProcessingTarget target = createTarget(NotificationType.SCHEDULE_D1_REMINDER, LocalDate.now());

        given(notificationService.claimPendingNotifications()).willReturn(List.of(target));

        notificationDispatchService.dispatchPendingNotifications();

        verify(notificationService).deferNotification(eq(target.notificationId()), eq(target.leaseId()), eq(LocalDateTime.of(2026, 11, 15, 9, 0)));

        verify(fcmService, never()).send(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("야간에도 P0 알림은 즉시 발송한다")
    void dispatchPendingNotifications_sendUrgentNotificationDuringNight() throws Exception {
        setFixedTime("2026-11-15T13:00:00Z");

        NotificationResDTO.NotificationProcessingTarget target = createTarget(NotificationType.SCHEDULE_CANCELLED, LocalDate.now());

        MemberDevice device = createDevice("test-token");

        given(notificationService.claimPendingNotifications()).willReturn(List.of(target));

        given(memberDeviceRepository.findAllByMemberId(1L)).willReturn(List.of(device));

        notificationDispatchService.dispatchPendingNotifications();

        verify(fcmService).send("test-token", target.title(), target.body(), target.notificationType());

        verify(notificationService).markNotificationSent(target.notificationId(), target.leaseId());

        verify(notificationService, never()).deferNotification(anyLong(), any(), any());
    }

    @Test
    @DisplayName("P5 알림의 일일 발송 제한에 도달하면 다음 날 오전 9시로 지연한다")
    void dispatchPendingNotifications_deferWhenDailyLimitReached() throws Exception {
        setFixedTime("2026-11-15T03:00:00Z");

        NotificationResDTO.NotificationProcessingTarget target = createTarget(NotificationType.QUESTION_REMINDER, LocalDate.of(2026, 11, 15));

        given(notificationService.claimPendingNotifications()).willReturn(List.of(target));

        given(notificationService.hasReachedDailyPushLimit(1L)).willReturn(true);

        notificationDispatchService.dispatchPendingNotifications();

        verify(notificationService).deferNotification(eq(target.notificationId()), eq(target.leaseId()), eq(LocalDateTime.of(2026, 11, 16, 9, 0)));

        verify(memberDeviceRepository, never()).findAllByMemberId(anyLong());
        verify(fcmService, never()).send(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("P5 알림의 일일 발송 제한에 도달하지 않으면 정상 발송한다")
    void dispatchPendingNotifications_sendWhenDailyLimitNotReached() throws Exception {
        setFixedTime("2026-11-15T03:00:00Z");

        NotificationResDTO.NotificationProcessingTarget target = createTarget(NotificationType.QUESTION_REMINDER, LocalDate.of(2026, 11, 15));

        MemberDevice device = createDevice("test-token");

        given(notificationService.claimPendingNotifications()).willReturn(List.of(target));

        given(notificationService.hasReachedDailyPushLimit(1L)).willReturn(false);

        given(memberDeviceRepository.findAllByMemberId(1L)).willReturn(List.of(device));

        notificationDispatchService.dispatchPendingNotifications();

        verify(fcmService).send("test-token", target.title(), target.body(), target.notificationType());

        verify(notificationService).markNotificationSent(target.notificationId(), target.leaseId());

        verify(notificationService, never()).deferNotification(anyLong(), any(), any());
    }

    @Test
    @DisplayName("P5가 아닌 알림은 일일 발송 제한을 검사하지 않는다")
    void dispatchPendingNotifications_skipDailyLimitForNonP5() throws Exception {
        setFixedTime("2026-11-15T03:00:00Z");

        NotificationResDTO.NotificationProcessingTarget target = createTarget(NotificationType.SCHEDULE, LocalDate.now());

        MemberDevice device = createDevice("test-token");

        given(notificationService.claimPendingNotifications()).willReturn(List.of(target));

        given(memberDeviceRepository.findAllByMemberId(1L)).willReturn(List.of(device));

        notificationDispatchService.dispatchPendingNotifications();

        verify(notificationService, never()).hasReachedDailyPushLimit(anyLong());

        verify(fcmService).send("test-token", target.title(), target.body(), target.notificationType());

        verify(notificationService).markNotificationSent(target.notificationId(), target.leaseId());
    }

    @Test
    @DisplayName("여러 디바이스 중 하나라도 FCM 발송에 성공하면 SENT 처리한다")
    void dispatchPendingNotifications_partialSuccess() throws Exception {
        setFixedTime("2026-11-15T03:00:00Z");

        NotificationResDTO.NotificationProcessingTarget target = createTarget(NotificationType.SCHEDULE, LocalDate.now());

        MemberDevice device1 = createDevice("token-1");
        MemberDevice device2 = createDevice("token-2");

        given(notificationService.claimPendingNotifications()).willReturn(List.of(target));

        given(memberDeviceRepository.findAllByMemberId(1L)).willReturn(List.of(device1, device2));

        given(fcmService.send("token-1", target.title(), target.body(), target.notificationType())).willReturn("success-response");

        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        given(exception.getMessagingErrorCode()).willReturn(null);

        given(fcmService.send("token-2", target.title(), target.body(), target.notificationType())).willThrow(exception);

        notificationDispatchService.dispatchPendingNotifications();

        verify(fcmService, times(1)).send("token-1", target.title(), target.body(), target.notificationType());

        verify(fcmService, times(1)).send("token-2", target.title(), target.body(), target.notificationType());

        verify(notificationService).markNotificationSent(target.notificationId(), target.leaseId());

        verify(notificationService, never()).markNotificationFailed(anyLong(), any());
    }

    @Test
    @DisplayName("모든 디바이스의 FCM 토큰이 유효하지 않으면 토큰을 삭제하고 SKIPPED 처리한다")
    void dispatchPendingNotifications_allTokensUnregistered() throws Exception {
        setFixedTime("2026-11-15T03:00:00Z");

        NotificationResDTO.NotificationProcessingTarget target = createTarget(NotificationType.SCHEDULE, LocalDate.now());

        MemberDevice device1 = createDevice("invalid-token-1");
        MemberDevice device2 = createDevice("invalid-token-2");

        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        given(exception.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNREGISTERED);

        given(notificationService.claimPendingNotifications()).willReturn(List.of(target));

        given(memberDeviceRepository.findAllByMemberId(1L)).willReturn(List.of(device1, device2));

        given(fcmService.send(anyString(), anyString(), anyString(), any())).willThrow(exception);

        notificationDispatchService.dispatchPendingNotifications();

        verify(memberDeviceRepository).deleteByFcmToken("invalid-token-1");
        verify(memberDeviceRepository).deleteByFcmToken("invalid-token-2");

        verify(notificationService).markNotificationSkipped(target.notificationId(), target.leaseId());

        verify(notificationService, never()).markNotificationSent(anyLong(), any());
        verify(notificationService, never()).markNotificationFailed(anyLong(), any());
    }

    @Test
    @DisplayName("FCM 프로젝트가 일치하지 않는 토큰은 삭제하고 SKIPPED 처리한다")
    void dispatchPendingNotifications_senderIdMismatch() throws Exception {
        setFixedTime("2026-11-15T03:00:00Z");

        NotificationResDTO.NotificationProcessingTarget target = createTarget(NotificationType.SCHEDULE, LocalDate.now());

        MemberDevice device = createDevice("mismatch-token");

        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        given(exception.getMessagingErrorCode()).willReturn(MessagingErrorCode.SENDER_ID_MISMATCH);

        given(notificationService.claimPendingNotifications()).willReturn(List.of(target));

        given(memberDeviceRepository.findAllByMemberId(1L)).willReturn(List.of(device));

        given(fcmService.send(anyString(), anyString(), anyString(), any())).willThrow(exception);

        notificationDispatchService.dispatchPendingNotifications();

        verify(memberDeviceRepository).deleteByFcmToken("mismatch-token");

        verify(notificationService).markNotificationSkipped(target.notificationId(), target.leaseId());

        verify(notificationService, never()).markNotificationFailed(anyLong(), any());
    }
}