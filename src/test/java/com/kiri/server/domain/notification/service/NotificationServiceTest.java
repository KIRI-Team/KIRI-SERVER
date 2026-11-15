package com.kiri.server.domain.notification.service;

import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.notification.entity.Notification;
import com.kiri.server.domain.notification.enums.NotificationStatus;
import com.kiri.server.domain.notification.enums.NotificationType;
import com.kiri.server.domain.notification.repository.NotificationRepository;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import com.kiri.server.domain.question.entity.MemberAnswer;
import com.kiri.server.domain.question.exception.QuestionException;
import com.kiri.server.domain.question.repository.FamilyQuestionRepository;
import com.kiri.server.domain.question.repository.MemberAnswerRepository;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.domain.schedule.repository.ScheduleParticipantRepository;
import com.kiri.server.domain.schedule.repository.ScheduleRepository;
import com.kiri.server.domain.setting.entity.MemberSetting;
import com.kiri.server.domain.setting.repository.MemberSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
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

    @Captor
    private ArgumentCaptor<Iterable<Notification>> notificationsCaptor;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ScheduleParticipantRepository scheduleParticipantRepository;

    private NotificationService notificationService;

    private final Family family = Family.builder()
            .id(1L)
            .name("행복한 우리집")
            .build();

    private final Member member = Member.builder()
            .id(MEMBER_ID)
            .nickname("철수")
            .family(family)
            .build();

    private final Member member2 = Member.builder()
            .id(2L)
            .nickname("영희")
            .family(family)
            .build();

    private final Member member3 = Member.builder()
            .id(3L)
            .family(family)
            .build();

    private final FamilyQuestion familyQuestion = FamilyQuestion.builder()
            .id(FAMILY_QUESTION_ID)
            .family(family)
            .questionDate(TODAY)
            .build();

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 10, 0);

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(familyQuestionRepository, memberRepository, memberSettingRepository, memberAnswerRepository, notificationRepository, scheduleRepository, scheduleParticipantRepository);
    }

    @Test
    @DisplayName("오늘 질문에 답변하지 않은 회원에게 질문 리마인드 알림을 생성한다.")
    void createQuestionReminderNotificationsSuccess() {
        given(familyQuestionRepository.findAllByQuestionDate(TODAY)).willReturn(List.of(familyQuestion));
        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(member));

        MemberSetting setting = createSetting(true, NOTIFICATION_TIME);

        given(memberSettingRepository.findByMember(member)).willReturn(Optional.of(setting));
        given(memberAnswerRepository.existsByFamilyQuestionAndMember(familyQuestion, member)).willReturn(false);
        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceDate(member, NotificationType.QUESTION_REMINDER, TODAY)).willReturn(false);

        notificationService.createQuestionReminderNotifications(TODAY, NOTIFICATION_TIME);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();

        assertThat(saved.getMember()).isEqualTo(member);
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.QUESTION_REMINDER);
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

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("전체 알림이 비활성화된 회원에게는 알림을 생성하지 않는다.")
    void createQuestionReminderNotificationsDisabled() {
        given(familyQuestionRepository.findAllByQuestionDate(TODAY)).willReturn(List.of(familyQuestion));
        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(member));
        given(memberSettingRepository.findByMember(member)).willReturn(Optional.of(createSetting(false, NOTIFICATION_TIME)));

        notificationService.createQuestionReminderNotifications(TODAY, NOTIFICATION_TIME);

        verify(notificationRepository, never()).save(any());
        verify(memberAnswerRepository, never()).existsByFamilyQuestionAndMember(familyQuestion, member);
    }

    @Test
    @DisplayName("회원의 알림 설정 시간이 아직 되지 않았으면 알림을 생성하지 않는다.")
    void createQuestionReminderNotificationsTimeNotReached() {
        given(familyQuestionRepository.findAllByQuestionDate(TODAY)).willReturn(List.of(familyQuestion));
        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(member));
        given(memberSettingRepository.findByMember(member)).willReturn(Optional.of(createSetting(true, LocalTime.of(21, 0))));

        notificationService.createQuestionReminderNotifications(TODAY, NOTIFICATION_TIME);

        verify(notificationRepository, never()).save(any());
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

        verify(notificationRepository, never()).save(any());
        verify(notificationRepository, never()).existsByMemberAndNotificationTypeAndReferenceDate(member, NotificationType.QUESTION_REMINDER, TODAY);
    }

    @Test
    @DisplayName("오늘 이미 생성된 질문 리마인드 알림이 있으면 중복 생성하지 않는다.")
    void createQuestionReminderNotificationsAlreadyExists() {
        given(familyQuestionRepository.findAllByQuestionDate(TODAY)).willReturn(List.of(familyQuestion));
        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(member));
        given(memberSettingRepository.findByMember(member)).willReturn(Optional.of(createSetting(true, NOTIFICATION_TIME)));
        given(memberAnswerRepository.existsByFamilyQuestionAndMember(familyQuestion, member)).willReturn(false);
        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceDate(member, NotificationType.QUESTION_REMINDER, TODAY)).willReturn(true);

        notificationService.createQuestionReminderNotifications(TODAY, NOTIFICATION_TIME);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("오늘 질문이 배정된 가족이 없으면 알림을 생성하지 않는다.")
    void createQuestionReminderNotificationsNoFamilyQuestion() {
        given(familyQuestionRepository.findAllByQuestionDate(TODAY)).willReturn(List.of());

        notificationService.createQuestionReminderNotifications(TODAY, NOTIFICATION_TIME);

        verify(memberRepository, never()).findAllByFamilyAndDeletedAtIsNull(any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("답변한 회원의 대기 중인 질문 리마인드 알림을 취소한다.")
    void cancelQuestionReminderSuccess() {
        Notification notification = Notification.builder()
                .member(member)
                .notificationType(NotificationType.QUESTION_REMINDER)
                .title("아직 오늘의 질문에 답변하지 않았어요!")
                .body("오늘 받은 질문카드는 오늘 내로 답변해 주세요. 가족들이 기다리고 있어요.")
                .status(NotificationStatus.PENDING)
                .referenceId(FAMILY_QUESTION_ID)
                .referenceDate(TODAY)
                .build();

        given(notificationRepository.findAllByMemberAndNotificationTypeAndReferenceIdAndStatus(member, NotificationType.QUESTION_REMINDER, FAMILY_QUESTION_ID, NotificationStatus.PENDING)).willReturn(List.of(notification));

        notificationService.cancelQuestionReminder(member, familyQuestion);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
    }

    @Test
    @DisplayName("취소할 질문 리마인드 알림이 없으면 아무 작업도 하지 않는다.")
    void cancelQuestionReminderNoNotification() {
        given(notificationRepository.findAllByMemberAndNotificationTypeAndReferenceIdAndStatus(member, NotificationType.QUESTION_REMINDER, FAMILY_QUESTION_ID, NotificationStatus.PENDING)).willReturn(List.of());

        notificationService.cancelQuestionReminder(member, familyQuestion);

        verify(notificationRepository).findAllByMemberAndNotificationTypeAndReferenceIdAndStatus(member, NotificationType.QUESTION_REMINDER, FAMILY_QUESTION_ID, NotificationStatus.PENDING);
    }

    @Test
    @DisplayName("가족 구성원 전원이 답변하면 마지막 답변자를 제외하고 알림을 생성한다.")
    void createFamilyAnswerCompleteNotificationsSuccess() {
        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(member, member2, member3));

        given(memberAnswerRepository.findAllByFamilyQuestion(familyQuestion)).willReturn(List.of(createAnswer(member), createAnswer(member2), createAnswer(member3)));

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceDate(member2, NotificationType.QUESTION_COMPLETE, TODAY)).willReturn(false);

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceDate(member3, NotificationType.QUESTION_COMPLETE, TODAY)).willReturn(false);

        notificationService.createFamilyAnswerCompleteNotifications(member, familyQuestion);

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> saved = captor.getValue();

        assertThat(saved).hasSize(2);
        assertThat(saved)
                .extracting(notification -> notification.getMember().getId())
                .containsExactlyInAnyOrder(2L, 3L);

        assertThat(saved).allSatisfy(notification -> {
            assertThat(notification.getNotificationType()).isEqualTo(NotificationType.QUESTION_COMPLETE);
            assertThat(notification.getTitle()).isEqualTo("오늘은 가족 모두가 답변했어요!");
            assertThat(notification.getBody()).isEqualTo("우리 가족의 생각이 모두 모였어요. 어떤 답변을 남겼는지 확인해 보세요.");
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(notification.getReferenceId()).isEqualTo(FAMILY_QUESTION_ID);
            assertThat(notification.getReferenceDate()).isEqualTo(TODAY);
        });
    }

    @Test
    @DisplayName("가족 구성원 중 답변하지 않은 회원이 있으면 알림을 생성하지 않는다.")
    void createFamilyAnswerCompleteNotificationsNotAllAnswered() {
        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(member, member2, member3));

        given(memberAnswerRepository.findAllByFamilyQuestion(familyQuestion)).willReturn(List.of(createAnswer(member), createAnswer(member2)));

        notificationService.createFamilyAnswerCompleteNotifications(member, familyQuestion);

        verify(notificationRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("이미 가족 전원 답변 완료 알림이 존재하면 중복 생성하지 않는다.")
    void createFamilyAnswerCompleteNotificationsAlreadyExists() {
        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(member, member2, member3));

        given(memberAnswerRepository.findAllByFamilyQuestion(familyQuestion)).willReturn(List.of(createAnswer(member), createAnswer(member2), createAnswer(member3)));

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceDate(member2, NotificationType.QUESTION_COMPLETE, TODAY)).willReturn(true);

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceDate(member3, NotificationType.QUESTION_COMPLETE, TODAY)).willReturn(true);

        notificationService.createFamilyAnswerCompleteNotifications(member, familyQuestion);

        verify(notificationRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("활성 구성원이 마지막 답변자 한 명뿐이면 알림을 생성하지 않는다.")
    void createFamilyAnswerCompleteNotificationsOnlyOneMember() {
        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(member));

        given(memberAnswerRepository.findAllByFamilyQuestion(familyQuestion)).willReturn(List.of(createAnswer(member)));

        notificationService.createFamilyAnswerCompleteNotifications(member, familyQuestion);

        verify(notificationRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("탈퇴한 구성원은 가족 전원 답변 완료 판단에서 제외한다.")
    void createFamilyAnswerCompleteNotificationsExcludesWithdrawnMember() {
        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(member, member2));

        given(memberAnswerRepository.findAllByFamilyQuestion(familyQuestion)).willReturn(List.of(createAnswer(member), createAnswer(member2)));

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceDate(member2, NotificationType.QUESTION_COMPLETE, TODAY)).willReturn(false);

        notificationService.createFamilyAnswerCompleteNotifications(member, familyQuestion);

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());

        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getMember()).isEqualTo(member2);
    }

    @Test
    @DisplayName("미답변 수신자에게 PK-01 알림 문구를 생성한다.")
    void createPokeNotificationNotAnswered() {
        given(familyQuestionRepository.findByFamilyAndQuestionDate(family, TODAY)).willReturn(Optional.of(familyQuestion));

        given(memberAnswerRepository.existsByFamilyQuestionAndMember(familyQuestion, member2)).willReturn(false);

        notificationService.createPokeNotification(member, member2, TODAY);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();

        assertThat(saved.getMember()).isEqualTo(member2);
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.POKE);
        assertThat(saved.getTitle()).isEqualTo("'철수'님이 나를 '콕' 찔렀어요!");
        assertThat(saved.getBody()).isEqualTo("철수님이 오늘의 질문 답변을 기다리고 있어요. 들어가서 확인해 주세요.");
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.getIsRead()).isFalse();
        assertThat(saved.getReferenceId()).isEqualTo(FAMILY_QUESTION_ID);
        assertThat(saved.getReferenceDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("답변 완료 수신자에게 PK-02 알림 문구를 생성한다.")
    void createPokeNotificationAnswered() {
        given(familyQuestionRepository.findByFamilyAndQuestionDate(family, TODAY)).willReturn(Optional.of(familyQuestion));

        given(memberAnswerRepository.existsByFamilyQuestionAndMember(familyQuestion, member2)).willReturn(true);

        notificationService.createPokeNotification(member, member2, TODAY);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();

        assertThat(saved.getMember()).isEqualTo(member2);
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.POKE);
        assertThat(saved.getTitle()).isEqualTo("'철수'님이 나를 '콕' 찔렀어요!");
        assertThat(saved.getBody()).isEqualTo("철수님이 관심을 보냈어요. 가족의 답변도 확인해 보세요.");
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.getIsRead()).isFalse();
        assertThat(saved.getReferenceId()).isEqualTo(FAMILY_QUESTION_ID);
        assertThat(saved.getReferenceDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("오늘 질문이 존재하지 않으면 예외가 발생한다.")
    void createPokeNotificationQuestionNotFound() {
        given(familyQuestionRepository.findByFamilyAndQuestionDate(family, TODAY)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.createPokeNotification(member, member2, TODAY)).isInstanceOf(QuestionException.class);

        verify(memberAnswerRepository, never()).existsByFamilyQuestionAndMember(familyQuestion, member2);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("콕 알림을 Notification 테이블에 저장한다.")
    void createPokeNotificationSaveNotification() {
        given(familyQuestionRepository.findByFamilyAndQuestionDate(family, TODAY)).willReturn(Optional.of(familyQuestion));

        given(memberAnswerRepository.existsByFamilyQuestionAndMember(familyQuestion, member2)).willReturn(false);

        notificationService.createPokeNotification(member, member2, TODAY);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();

        assertThat(saved).isNotNull();
        assertThat(saved.getMember()).isEqualTo(member2);
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.POKE);
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    @DisplayName("주간 미션 배정 시 활성 가족 구성원 모두에게 알림을 생성한다.")
    void createWeeklyMissionNotificationsSuccess() {
        Mission mission = Mission.builder()
                .id(100L)
                .title("공원 산책")
                .build();

        LocalDate referenceDate = LocalDate.of(2026, 11, 9);

        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(member, member2, member3));

        notificationService.createWeeklyMissionNotifications(family, mission, referenceDate);

        then(memberRepository).should().findAllByFamilyAndDeletedAtIsNull(family);

        then(notificationRepository).should().saveAll(notificationsCaptor.capture());

        List<Notification> notifications = StreamSupport.stream(notificationsCaptor.getValue().spliterator(), false).toList();

        assertThat(notifications).hasSize(3);

        assertThat(notifications)
                .extracting(Notification::getMember)
                .containsExactlyInAnyOrder(member, member2, member3);

        assertThat(notifications)
                .extracting(Notification::getNotificationType)
                .containsOnly(NotificationType.MISSION);

        assertThat(notifications)
                .extracting(Notification::getTitle)
                .containsOnly("이번 주 새로운 가족 미션이 도착했어요!");

        assertThat(notifications)
                .extracting(Notification::getBody)
                .containsOnly("이번 주 우리 가족의 미션은 '공원 산책'이에요. 함께할 날짜를 정해 보세요.");

        assertThat(notifications)
                .extracting(Notification::getStatus)
                .containsOnly(NotificationStatus.PENDING);

        assertThat(notifications)
                .extracting(Notification::getIsRead)
                .containsOnly(false);

        assertThat(notifications)
                .extracting(Notification::getReferenceId)
                .containsOnly(100L);

        assertThat(notifications)
                .extracting(Notification::getReferenceDate)
                .containsOnly(referenceDate);
    }

    @Test
    @DisplayName("활성 가족 구성원이 없으면 주간 미션 알림을 생성하지 않는다.")
    void createWeeklyMissionNotificationsNoMembers() {
        Mission mission = Mission.builder()
                .id(100L)
                .title("공원 산책")
                .build();

        LocalDate referenceDate = LocalDate.of(2026, 11, 9);

        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of());

        notificationService.createWeeklyMissionNotifications(family, mission, referenceDate);

        then(memberRepository).should().findAllByFamilyAndDeletedAtIsNull(family);

        then(notificationRepository).should(never()).saveAll(anyList());
    }

    @Test
    @DisplayName("MS-02. 가족 구성원에게 주중 미션 변경 알림을 생성한다.")
    void createMissionChangedNotificationsSuccess() {
        Mission mission = Mission.builder()
                .id(100L)
                .title("함께 요리하기")
                .build();

        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(member, member2));

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);

        notificationService.createMissionChangedNotifications(family, mission, TODAY);

        then(notificationRepository).should().saveAll(captor.capture());

        List<Notification> notifications = captor.getValue();

        assertThat(notifications).hasSize(2);

        assertThat(notifications)
                .extracting(Notification::getNotificationType)
                .containsOnly(NotificationType.MISSION_CHANGED);

        assertThat(notifications)
                .extracting(Notification::getTitle)
                .containsOnly("이번 주 가족 미션이 변경됐어요!");

        assertThat(notifications)
                .extracting(Notification::getBody)
                .containsOnly("기존 미션을 진행할 수 없어 새로운 미션 '함께 요리하기'으로 변경했어요. 함께할 날짜를 정해 보세요.");

        assertThat(notifications)
                .extracting(Notification::getStatus)
                .containsOnly(NotificationStatus.PENDING);

        assertThat(notifications)
                .extracting(Notification::getIsRead)
                .containsOnly(false);

        assertThat(notifications)
                .extracting(Notification::getReferenceId)
                .containsOnly(100L);

        assertThat(notifications)
                .extracting(Notification::getReferenceDate)
                .containsOnly(TODAY);

        assertThat(notifications)
                .extracting(Notification::getMember)
                .containsExactly(member, member2);
    }

    @Test
    @DisplayName("MS-02. 가족 구성원이 없으면 주중 미션 변경 알림을 생성하지 않는다.")
    void createMissionChangedNotificationsNoMembers() {
        Mission mission = Mission.builder()
                .id(100L)
                .title("함께 요리하기")
                .build();

        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of());

        notificationService.createMissionChangedNotifications(family, mission, TODAY);

        then(notificationRepository).should(never()).saveAll(anyList());
    }
    @Test
    @DisplayName("SC-01. 일정 초대 대상자에게 일정 초대 알림을 생성한다.")
    void createScheduleInvitationNotificationsSuccess() {
        Schedule schedule = createSchedule();

        ScheduleParticipant participant1 = createParticipant(schedule, member, ParticipantStatus.PENDING);
        ScheduleParticipant participant2 = createParticipant(schedule, member2, ParticipantStatus.PENDING);

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member, NotificationType.SCHEDULE_INVITATION, schedule.getId())).willReturn(false);

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member2, NotificationType.SCHEDULE_INVITATION, schedule.getId())).willReturn(false);

        notificationService.createScheduleInvitationNotifications(schedule, List.of(participant1, participant2));

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);

        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> saved = captor.getValue();

        assertThat(saved).hasSize(2);
        assertThat(saved)
                .extracting(Notification::getMember)
                .containsExactlyInAnyOrder(member, member2);

        assertThat(saved)
                .extracting(Notification::getNotificationType)
                .containsOnly(NotificationType.SCHEDULE_INVITATION);

        assertThat(saved)
                .extracting(Notification::getStatus)
                .containsOnly(NotificationStatus.PENDING);

        assertThat(saved)
                .extracting(Notification::getReferenceId)
                .containsOnly(schedule.getId());
    }

    @Test
    @DisplayName("SC-01. 일정 제안자는 일정 초대 알림 대상에서 제외한다.")
    void createScheduleInvitationNotificationsExcludeProposer() {
        Schedule schedule = createSchedule();

        ScheduleParticipant proposer = createParticipant(schedule, member, ParticipantStatus.ACCEPTED);
        ScheduleParticipant invitee = createParticipant(schedule, member2, ParticipantStatus.PENDING);

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member2, NotificationType.SCHEDULE_INVITATION, schedule.getId())).willReturn(false);

        notificationService.createScheduleInvitationNotifications(schedule, List.of(proposer, invitee));

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);

        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> saved = captor.getValue();

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getMember()).isEqualTo(member2);

        verify(notificationRepository, never()).existsByMemberAndNotificationTypeAndReferenceId(member, NotificationType.SCHEDULE_INVITATION, schedule.getId());
    }

    @Test
    @DisplayName("SC-01. 일정 초대 대상자가 없으면 알림을 생성하지 않는다.")
    void createScheduleInvitationNotificationsNoInvitees() {
        Schedule schedule = createSchedule();

        ScheduleParticipant proposer = createParticipant(schedule, member, ParticipantStatus.ACCEPTED);

        notificationService.createScheduleInvitationNotifications(schedule, List.of(proposer));

        verify(notificationRepository, never()).saveAll(anyList());

        verify(notificationRepository, never()).existsByMemberAndNotificationTypeAndReferenceId(any(Member.class), eq(NotificationType.SCHEDULE_INVITATION), eq(schedule.getId()));
    }

    @Test
    @DisplayName("SC-01. 이미 일정 초대 알림이 존재하면 중복 생성하지 않는다.")
    void createScheduleInvitationNotificationsPreventDuplicate() {
        Schedule schedule = createSchedule();

        ScheduleParticipant participant = createParticipant(schedule, member2, ParticipantStatus.PENDING);

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member2, NotificationType.SCHEDULE_INVITATION, schedule.getId())).willReturn(true);

        notificationService.createScheduleInvitationNotifications(schedule, List.of(participant));

        verify(notificationRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("SC-02. 일정 초대에 응답하지 않은 회원에게 리마인더 알림을 생성한다.")
    void createScheduleInvitationReminderNotificationsSuccess() {
        Schedule schedule = createReminderSchedule(NOW.plusHours(1));

        ScheduleParticipant participant = createParticipant(schedule, member2, ParticipantStatus.PENDING);

        given(scheduleRepository.findByIdForUpdate(schedule.getId())).willReturn(Optional.of(schedule));

        given(scheduleParticipantRepository.findAllWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.PENDING)).willReturn(List.of(participant));

        given(notificationRepository
                .existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member2, NotificationType.SCHEDULE_INVITATION_REMINDER, schedule.getId(), 1))
                .willReturn(false);

        notificationService.createScheduleInvitationReminderNotifications(schedule.getId(), NOW);

        ArgumentCaptor<Iterable<Notification>> captor = ArgumentCaptor.forClass(Iterable.class);

        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> saved = StreamSupport.stream(captor.getValue().spliterator(), false).toList();

        assertThat(saved).hasSize(1);

        Notification notification = saved.get(0);

        assertThat(notification.getMember()).isEqualTo(member2);
        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.SCHEDULE_INVITATION_REMINDER);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getReferenceId()).isEqualTo(schedule.getId());
        assertThat(notification.getReferenceVersion()).isEqualTo(1);

        assertThat(schedule.getResponseReminderAt()).isNull();
    }

    @Test
    @DisplayName("SC-02. 동일한 초대 버전의 리마인더 알림이 존재하면 중복 생성하지 않는다.")
    void createScheduleInvitationReminderNotificationsPreventDuplicate() {
        Schedule schedule = createReminderSchedule(NOW.plusHours(1));

        ScheduleParticipant participant = createParticipant(schedule, member2, ParticipantStatus.PENDING);

        given(scheduleRepository.findByIdForUpdate(schedule.getId())).willReturn(Optional.of(schedule));

        given(scheduleParticipantRepository.findAllWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.PENDING)).willReturn(List.of(participant));

        given(notificationRepository
                .existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member2, NotificationType.SCHEDULE_INVITATION_REMINDER, schedule.getId(), 1))
                .willReturn(true);

        notificationService.createScheduleInvitationReminderNotifications(schedule.getId(), NOW);

        verify(notificationRepository, never()).saveAll(anyList());

        assertThat(schedule.getResponseReminderAt()).isNull();
    }

    @Test
    @DisplayName("SC-02. 응답 마감 시간이 지난 일정은 리마인더를 생성하지 않는다.")
    void createScheduleInvitationReminderNotificationsDeadlineExpired() {
        Schedule schedule = createReminderSchedule(NOW.minusMinutes(1));

        given(scheduleRepository.findByIdForUpdate(schedule.getId())).willReturn(Optional.of(schedule));

        notificationService.createScheduleInvitationReminderNotifications(schedule.getId(), NOW);

        verify(scheduleParticipantRepository, never()).findAllWithMemberByScheduleIdAndStatus(anyLong(), any());

        verify(notificationRepository, never()).saveAll(anyList());

        assertThat(schedule.getResponseReminderAt()).isNull();
    }

    @Test
    @DisplayName("SC-02. 리마인더 발송 시각이 아직 되지 않았다면 알림을 생성하지 않는다.")
    void createScheduleInvitationReminderNotificationsNotDue() {
        Schedule schedule = createReminderSchedule(NOW.plusHours(1));
        LocalDateTime reminderAt = NOW.plusMinutes(5);

        schedule.updateResponseReminderAt(reminderAt);

        given(scheduleRepository.findByIdForUpdate(schedule.getId())).willReturn(Optional.of(schedule));

        notificationService.createScheduleInvitationReminderNotifications(schedule.getId(), NOW);

        verify(scheduleParticipantRepository, never()).findAllWithMemberByScheduleIdAndStatus(anyLong(), any());

        verify(notificationRepository, never()).saveAll(anyList());

        assertThat(schedule.getResponseReminderAt()).isEqualTo(reminderAt);
    }


    private MemberSetting createSetting(boolean enabled, LocalTime notificationTime) {
        return MemberSetting.builder()
                .member(member)
                .isAllNotificationEnabled(enabled)
                .regularNotificationTime(notificationTime)
                .build();
    }

    private MemberAnswer createAnswer(Member answerer) {
        return MemberAnswer.builder()
                .content("오늘의 답변입니다.")
                .familyQuestion(familyQuestion)
                .member(answerer)
                .build();
    }

    private Schedule createSchedule() {
        Mission mission = Mission.builder()
                .id(100L)
                .title("공원 산책")
                .build();

        BucketItem bucketItem = BucketItem.builder()
                .id(200L)
                .mission(mission)
                .build();

        return Schedule.builder()
                .id(100L)
                .family(family)
                .bucketItem(bucketItem)
                .proposer(member)
                .scheduleDate(TODAY)
                .scheduleTime(LocalTime.of(14, 0))
                .build();
    }

    private Schedule createReminderSchedule(LocalDateTime responseDeadline) {
        Mission mission = Mission.builder()
                .id(100L)
                .title("공원 산책")
                .build();

        BucketItem bucketItem = BucketItem.builder()
                .id(200L)
                .mission(mission)
                .build();

        return Schedule.builder()
                .id(300L)
                .family(family)
                .bucketItem(bucketItem)
                .proposer(member)
                .scheduleDate(TODAY)
                .scheduleTime(LocalTime.of(14, 0))
                .status(ScheduleStatus.PENDING_RESPONSE)
                .responseDeadline(responseDeadline)
                .responseReminderAt(NOW.minusMinutes(1))
                .inviteVersion(1)
                .build();
    }

    private ScheduleParticipant createParticipant(Schedule schedule, Member participant, ParticipantStatus status) {
        return ScheduleParticipant.builder()
                .schedule(schedule)
                .member(participant)
                .status(status)
                .build();
    }
}