package com.kiri.server.domain.notification.service;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.notification.entity.Notification;
import com.kiri.server.domain.notification.enums.NotificationPriority;
import com.kiri.server.domain.notification.enums.NotificationStatus;
import com.kiri.server.domain.notification.enums.NotificationType;
import com.kiri.server.domain.notification.repository.NotificationRepository;
import com.kiri.server.domain.question.repository.FamilyQuestionRepository;
import com.kiri.server.domain.question.repository.MemberAnswerRepository;
import com.kiri.server.domain.review.repository.ReviewRepository;
import com.kiri.server.domain.schedule.repository.ScheduleParticipantRepository;
import com.kiri.server.domain.schedule.repository.ScheduleRepository;
import com.kiri.server.domain.setting.repository.MemberSettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class NotificationProcessingServiceTest {

    @Mock
    private FamilyQuestionRepository familyQuestionRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberSettingRepository memberSettingRepository;

    @Mock
    private MemberAnswerRepository memberAnswerRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ScheduleParticipantRepository scheduleParticipantRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @InjectMocks
    private NotificationService notificationService;

    private Notification createNotification(Member member) {
        Notification notification = Notification.builder()
                .member(member)
                .notificationType(NotificationType.QUESTION_REMINDER)
                .title("질문 알림")
                .body("오늘의 질문에 답변해 주세요.")
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceDate(LocalDateTime.now().toLocalDate())
                .priority(NotificationPriority.P5)
                .build();

        ReflectionTestUtils.setField(notification, "id", 1L);

        return notification;
    }

    @Test
    @DisplayName("대기 중인 알림을 선점하고 발송 대상으로 반환한다")
    void claimPendingNotifications() {
        Member member = org.mockito.Mockito.mock(Member.class);
        given(member.getId()).willReturn(10L);

        Notification notification = createNotification(member);

        given(notificationRepository.findPendingNotificationsForProcessing(any(LocalDateTime.class), eq(100))).willReturn(List.of(notification));

        var targets = notificationService.claimPendingNotifications();

        assertThat(targets).hasSize(1);
        assertThat(targets.get(0).notificationId()).isEqualTo(1L);
        assertThat(targets.get(0).memberId()).isEqualTo(10L);
        assertThat(targets.get(0).title()).isEqualTo("질문 알림");
        assertThat(targets.get(0).leaseId()).isNotNull();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
        assertThat(notification.getProcessingLeaseId()).isEqualTo(targets.get(0).leaseId());

        verify(notificationRepository).findPendingNotificationsForProcessing(any(LocalDateTime.class), eq(100));
    }

    @Test
    @DisplayName("처리 중단된 알림을 대기 상태로 복구한다")
    void recoverStaleProcessingNotifications() {
        Member member = org.mockito.Mockito.mock(Member.class);
        Notification notification = createNotification(member);

        notification.startProcessing();

        given(notificationRepository.findStaleProcessingNotifications(any(LocalDateTime.class), eq(100))).willReturn(List.of(notification));

        int recoveredCount = notificationService.recoverStaleProcessingNotifications();

        assertThat(recoveredCount).isEqualTo(1);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getProcessingAt()).isNull();
        assertThat(notification.getProcessingLeaseId()).isNull();
    }

    @Test
    @DisplayName("발송 성공한 알림을 SENT 상태로 변경한다")
    void markNotificationSent() {
        Member member = org.mockito.Mockito.mock(Member.class);
        Notification notification = createNotification(member);
        UUID leaseId = notification.startProcessing();

        given(notificationRepository.findById(1L)).willReturn(Optional.of(notification));

        notificationService.markNotificationSent(1L, leaseId);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isNotNull();
        assertThat(notification.getProcessingLeaseId()).isNull();
    }

    @Test
    @DisplayName("발송 실패한 알림을 FAILED 상태로 변경한다")
    void markNotificationFailed() {
        Member member = org.mockito.Mockito.mock(Member.class);
        Notification notification = createNotification(member);
        UUID leaseId = notification.startProcessing();

        given(notificationRepository.findById(1L)).willReturn(Optional.of(notification));

        notificationService.markNotificationFailed(1L, leaseId);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getRetryCount()).isEqualTo(1);
        assertThat(notification.getNextRetryAt()).isNotNull();
        assertThat(notification.getProcessingLeaseId()).isNull();
    }

    @Test
    @DisplayName("발송 대상에서 제외된 알림을 SKIPPED 상태로 변경한다")
    void markNotificationSkipped() {
        Member member = org.mockito.Mockito.mock(Member.class);
        Notification notification = createNotification(member);
        UUID leaseId = notification.startProcessing();

        given(notificationRepository.findById(1L)).willReturn(Optional.of(notification));

        notificationService.markNotificationSkipped(1L, leaseId);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(notification.getProcessingLeaseId()).isNull();
    }

    @Test
    @DisplayName("재시도 가능한 실패 알림을 다시 대기 상태로 변경한다")
    void retryFailedNotifications() {
        Member member = org.mockito.Mockito.mock(Member.class);
        Notification notification = createNotification(member);

        UUID leaseId = notification.startProcessing();
        notification.fail(leaseId);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);

        given(notificationRepository.findRetryableNotifications(any(LocalDateTime.class), eq(100))).willReturn(List.of(notification));

        int retriedCount = notificationService.retryFailedNotifications();

        assertThat(retriedCount).isEqualTo(1);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getNextRetryAt()).isNull();
        assertThat(notification.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("일일 P5 알림 발송 횟수가 한도 미만이면 false를 반환한다")
    void hasNotReachedDailyPushLimit() {
        given(notificationRepository.countByMember_IdAndPriorityAndStatusAndSentAtGreaterThanEqualAndSentAtLessThan(eq(10L), eq(NotificationPriority.P5), eq(NotificationStatus.SENT), any(LocalDateTime.class), any(LocalDateTime.class))).willReturn(1L);

        boolean result = notificationService.hasReachedDailyPushLimit(10L);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("일일 P5 알림 발송 횟수가 한도에 도달하면 true를 반환한다")
    void hasReachedDailyPushLimit() {
        given(notificationRepository.countByMember_IdAndPriorityAndStatusAndSentAtGreaterThanEqualAndSentAtLessThan(eq(10L), eq(NotificationPriority.P5), eq(NotificationStatus.SENT), any(LocalDateTime.class), any(LocalDateTime.class))).willReturn(2L);

        boolean result = notificationService.hasReachedDailyPushLimit(10L);

        assertThat(result).isTrue();
    }
}
