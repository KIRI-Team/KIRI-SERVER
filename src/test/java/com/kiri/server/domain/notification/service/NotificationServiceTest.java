package com.kiri.server.domain.notification.service;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.notification.entity.Notification;
import com.kiri.server.domain.notification.enums.NotificationStatus;
import com.kiri.server.domain.notification.enums.NotificationType;
import com.kiri.server.domain.notification.repository.NotificationRepository;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import com.kiri.server.domain.question.repository.FamilyQuestionRepository;
import com.kiri.server.domain.question.repository.MemberAnswerRepository;
import com.kiri.server.domain.setting.entity.MemberSetting;
import com.kiri.server.domain.setting.repository.MemberSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long FAMILY_QUESTION_ID = 10L;

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final LocalTime NOTIFICATION_TIME = LocalTime.of(20, 0);

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

    private NotificationService notificationService;

    private final Family family = Family.builder()
            .id(1L)
            .name("행복한 우리집")
            .build();

    private final Member member = Member.builder()
            .id(MEMBER_ID)
            .family(family)
            .build();

    private final FamilyQuestion familyQuestion = FamilyQuestion.builder()
            .id(FAMILY_QUESTION_ID)
            .family(family)
            .questionDate(TODAY)
            .build();

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(familyQuestionRepository, memberRepository, memberSettingRepository, memberAnswerRepository, notificationRepository);
    }

    @Test
    @DisplayName("오늘 질문에 답변하지 않은 회원에게 질문 리마인드 알림을 생성한다.")
    void createQuestionReminderNotificationsSuccess() {
        given(familyQuestionRepository.findAllByQuestionDate(TODAY)).willReturn(List.of(familyQuestion));
        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(member));

        MemberSetting setting = createSetting(true, NOTIFICATION_TIME);

        given(memberSettingRepository.findByMember(member)).willReturn(Optional.of(setting));
        given(memberAnswerRepository.existsByFamilyQuestionAndMember(familyQuestion, member)).willReturn(false);
        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceDate(member, NotificationType.QUESTION, TODAY)).willReturn(false);

        notificationService.createQuestionReminderNotifications(TODAY, NOTIFICATION_TIME);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();

        assertThat(saved.getMember()).isEqualTo(member);
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.QUESTION);
        assertThat(saved.getTitle()).isEqualTo("아직 오늘의 질문에 답변하지 않았어요!");
        assertThat(saved.getBody()).isEqualTo("오늘 받은 질문카드는 오늘 내로 답변해 주세요. 가족들이 기다리고 있어요.");
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.getReferenceId()).isEqualTo(FAMILY_QUESTION_ID);
        assertThat(saved.getReferenceDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("회원 알림 설정이 존재하지 않으면 알림을 생성하지 않는다.")
    void createQuestionReminderNotificationsSettingNotFound() {
        given(familyQuestionRepository.findAllByQuestionDate(TODAY)).willReturn(List.of(familyQuestion));
        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(member));
        given(memberSettingRepository.findByMember(member)).willReturn(Optional.empty());

        notificationService.createQuestionReminderNotifications(TODAY, NOTIFICATION_TIME);

        verify(notificationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("전체 알림이 비활성화된 회원에게는 알림을 생성하지 않는다.")
    void createQuestionReminderNotificationsDisabled() {
        given(familyQuestionRepository.findAllByQuestionDate(TODAY)).willReturn(List.of(familyQuestion));
        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(member));
        given(memberSettingRepository.findByMember(member)).willReturn(Optional.of(createSetting(false, NOTIFICATION_TIME)));

        notificationService.createQuestionReminderNotifications(TODAY, NOTIFICATION_TIME);

        verify(notificationRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(memberAnswerRepository, never()).existsByFamilyQuestionAndMember(familyQuestion, member);
    }

    @Test
    @DisplayName("회원의 알림 설정 시간이 아직 되지 않았으면 알림을 생성하지 않는다.")
    void createQuestionReminderNotificationsTimeNotReached() {
        given(familyQuestionRepository.findAllByQuestionDate(TODAY)).willReturn(List.of(familyQuestion));
        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(member));
        given(memberSettingRepository.findByMember(member)).willReturn(Optional.of(createSetting(true, LocalTime.of(21, 0))));

        notificationService.createQuestionReminderNotifications(TODAY, NOTIFICATION_TIME);

        verify(notificationRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(memberAnswerRepository, never()).existsByFamilyQuestionAndMember(familyQuestion, member);
    }

    @Test
    @DisplayName("오늘 질문에 이미 답변한 회원에게는 알림을 생성하지 않는다.")
    void createQuestionReminderNotificationsAlreadyAnswered() {
        given(familyQuestionRepository.findAllByQuestionDate(TODAY)).willReturn(List.of(familyQuestion));
        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(member));
        given(memberSettingRepository.findByMember(member)).willReturn(Optional.of(createSetting(true, NOTIFICATION_TIME)));
        given(memberAnswerRepository.existsByFamilyQuestionAndMember(familyQuestion, member)).willReturn(true);

        notificationService.createQuestionReminderNotifications(TODAY, NOTIFICATION_TIME);

        verify(notificationRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(notificationRepository, never()).existsByMemberAndNotificationTypeAndReferenceDate(member, NotificationType.QUESTION, TODAY);
    }

    @Test
    @DisplayName("오늘 이미 생성된 질문 리마인드 알림이 있으면 중복 생성하지 않는다.")
    void createQuestionReminderNotificationsAlreadyExists() {
        given(familyQuestionRepository.findAllByQuestionDate(TODAY)).willReturn(List.of(familyQuestion));
        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(member));
        given(memberSettingRepository.findByMember(member)).willReturn(Optional.of(createSetting(true, NOTIFICATION_TIME)));
        given(memberAnswerRepository.existsByFamilyQuestionAndMember(familyQuestion, member)).willReturn(false);
        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceDate(member, NotificationType.QUESTION, TODAY)).willReturn(true);

        notificationService.createQuestionReminderNotifications(TODAY, NOTIFICATION_TIME);

        verify(notificationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("오늘 질문이 배정된 가족이 없으면 알림을 생성하지 않는다.")
    void createQuestionReminderNotificationsNoFamilyQuestion() {
        given(familyQuestionRepository.findAllByQuestionDate(TODAY)).willReturn(List.of());

        notificationService.createQuestionReminderNotifications(TODAY, NOTIFICATION_TIME);

        verify(memberRepository, never()).findAllByFamilyAndDeletedAtIsNull(org.mockito.ArgumentMatchers.any());
        verify(notificationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("답변한 회원의 대기 중인 질문 리마인드 알림을 취소한다.")
    void cancelQuestionReminderSuccess() {
        Notification notification = Notification.builder()
                .member(member)
                .notificationType(NotificationType.QUESTION)
                .title("아직 오늘의 질문에 답변하지 않았어요!")
                .body("오늘 받은 질문카드는 오늘 내로 답변해 주세요. 가족들이 기다리고 있어요.")
                .status(NotificationStatus.PENDING)
                .referenceId(FAMILY_QUESTION_ID)
                .referenceDate(TODAY)
                .build();

        given(notificationRepository.findAllByMemberAndNotificationTypeAndReferenceIdAndStatus(member, NotificationType.QUESTION, FAMILY_QUESTION_ID, NotificationStatus.PENDING)).willReturn(List.of(notification));

        notificationService.cancelQuestionReminder(member, familyQuestion);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
    }

    @Test
    @DisplayName("취소할 질문 리마인드 알림이 없으면 아무 작업도 하지 않는다.")
    void cancelQuestionReminderNoNotification() {
        given(notificationRepository.findAllByMemberAndNotificationTypeAndReferenceIdAndStatus(member, NotificationType.QUESTION, FAMILY_QUESTION_ID, NotificationStatus.PENDING)).willReturn(List.of());

        notificationService.cancelQuestionReminder(member, familyQuestion);

        verify(notificationRepository).findAllByMemberAndNotificationTypeAndReferenceIdAndStatus(member, NotificationType.QUESTION, FAMILY_QUESTION_ID, NotificationStatus.PENDING);
    }

    private MemberSetting createSetting(boolean enabled, LocalTime notificationTime) {
        return MemberSetting.builder()
                .member(member)
                .isAllNotificationEnabled(enabled)
                .regularNotificationTime(notificationTime)
                .build();
    }
}