package com.kiri.server.domain.notification.service;

import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.entity.FamilyMissionRecommendation;
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
import com.kiri.server.domain.review.repository.ReviewRepository;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
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
import static org.mockito.Mockito.*;

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

    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ScheduleParticipantRepository scheduleParticipantRepository;

    @Mock
    private ReviewRepository reviewRepository;

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
        notificationService = new NotificationService(familyQuestionRepository, memberRepository, memberSettingRepository, memberAnswerRepository, notificationRepository, scheduleRepository, scheduleParticipantRepository, reviewRepository);
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

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member, NotificationType.SCHEDULE_INVITATION, schedule.getId(), schedule.getInviteVersion())).willReturn(false);

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member2, NotificationType.SCHEDULE_INVITATION, schedule.getId(), schedule.getInviteVersion())).willReturn(false);

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

        assertThat(saved)
                .extracting(Notification::getReferenceVersion)
                .containsOnly(schedule.getInviteVersion());
    }

    @Test
    @DisplayName("SC-01. 일정 제안자는 일정 초대 알림 대상에서 제외한다.")
    void createScheduleInvitationNotificationsExcludeProposer() {
        Schedule schedule = createSchedule();

        ScheduleParticipant proposer = createParticipant(schedule, member, ParticipantStatus.ACCEPTED);
        ScheduleParticipant invitee = createParticipant(schedule, member2, ParticipantStatus.PENDING);

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member2, NotificationType.SCHEDULE_INVITATION, schedule.getId(), schedule.getInviteVersion())).willReturn(false);

        notificationService.createScheduleInvitationNotifications(schedule, List.of(proposer, invitee));

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);

        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> saved = captor.getValue();

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getMember()).isEqualTo(member2);

        verify(notificationRepository, never()).existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member, NotificationType.SCHEDULE_INVITATION, schedule.getId(), schedule.getInviteVersion());
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

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member2, NotificationType.SCHEDULE_INVITATION, schedule.getId(), schedule.getInviteVersion())).willReturn(true);

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

    @Test
    @DisplayName("SC-03. 일정이 확정되면 제안자와 수락한 참여자에게 알림을 생성한다.")
    void createScheduleConfirmedNotificationsSuccess() {
        Schedule schedule = createSchedule();

        ScheduleParticipant acceptedParticipant = createParticipant(schedule, member2, ParticipantStatus.ACCEPTED);

        ScheduleParticipant declinedParticipant = createParticipant(schedule, member3, ParticipantStatus.DECLINED);

        given(scheduleParticipantRepository.findAllWithMemberByScheduleId(schedule.getId())).willReturn(List.of(acceptedParticipant, declinedParticipant));

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member, NotificationType.SCHEDULE, schedule.getId())).willReturn(false);

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member2, NotificationType.SCHEDULE, schedule.getId())).willReturn(false);

        notificationService.createScheduleConfirmedNotifications(schedule);

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);

        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> saved = captor.getValue();

        assertThat(saved).hasSize(2);

        assertThat(saved)
                .extracting(Notification::getMember)
                .containsExactlyInAnyOrder(member, member2);

        assertThat(saved)
                .extracting(Notification::getNotificationType)
                .containsOnly(NotificationType.SCHEDULE);

        assertThat(saved)
                .extracting(Notification::getTitle)
                .containsOnly("일정이 확정됐어요! 🎉");

        assertThat(saved)
                .extracting(Notification::getBody)
                .containsOnly("'공원 산책' 일정이 확정됐어요. 11월 15일 14:00에 만나요!");

        assertThat(saved)
                .extracting(Notification::getStatus)
                .containsOnly(NotificationStatus.PENDING);

        assertThat(saved)
                .extracting(Notification::getIsRead)
                .containsOnly(false);

        assertThat(saved)
                .extracting(Notification::getReferenceId)
                .containsOnly(schedule.getId());
    }

    @Test
    @DisplayName("SC-03. 거절하거나 응답하지 않은 참여자에게는 확정 알림을 생성하지 않는다.")
    void createScheduleConfirmedNotificationsExcludesNonAccepted() {
        Schedule schedule = createSchedule();

        ScheduleParticipant declined = createParticipant(schedule, member2, ParticipantStatus.DECLINED);

        ScheduleParticipant pending = createParticipant(schedule, member3, ParticipantStatus.PENDING);

        given(scheduleParticipantRepository.findAllWithMemberByScheduleId(schedule.getId())).willReturn(List.of(declined, pending));

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member, NotificationType.SCHEDULE, schedule.getId())).willReturn(false);

        notificationService.createScheduleConfirmedNotifications(schedule);

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);

        verify(notificationRepository).saveAll(captor.capture());

        assertThat(captor.getValue())
                .hasSize(1)
                .extracting(Notification::getMember)
                .containsExactly(member);
    }

    @Test
    @DisplayName("SC-03. 제안자가 참여자 목록에도 포함되어 있으면 확정 알림을 중복 생성하지 않는다.")
    void createScheduleConfirmedNotificationsExcludeDuplicateProposer() {
        Schedule schedule = createSchedule();

        ScheduleParticipant proposerParticipant = createParticipant(schedule, member, ParticipantStatus.ACCEPTED);

        ScheduleParticipant acceptedParticipant = createParticipant(schedule, member2, ParticipantStatus.ACCEPTED);

        given(scheduleParticipantRepository.findAllWithMemberByScheduleId(schedule.getId())).willReturn(List.of(proposerParticipant, acceptedParticipant));

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member, NotificationType.SCHEDULE, schedule.getId())).willReturn(false);

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member2, NotificationType.SCHEDULE, schedule.getId())).willReturn(false);

        notificationService.createScheduleConfirmedNotifications(schedule);

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);

        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> saved = captor.getValue();

        assertThat(saved).hasSize(2);

        assertThat(saved)
                .extracting(notification -> notification.getMember().getId())
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("SC-03. 이미 확정 알림이 존재하는 회원에게는 중복 생성하지 않는다.")
    void createScheduleConfirmedNotificationsPreventDuplicate() {
        Schedule schedule = createSchedule();

        ScheduleParticipant acceptedParticipant = createParticipant(schedule, member2, ParticipantStatus.ACCEPTED);

        given(scheduleParticipantRepository.findAllWithMemberByScheduleId(schedule.getId())).willReturn(List.of(acceptedParticipant));

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member, NotificationType.SCHEDULE, schedule.getId())).willReturn(true);

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member2, NotificationType.SCHEDULE, schedule.getId())).willReturn(true);

        notificationService.createScheduleConfirmedNotifications(schedule);

        verify(notificationRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("SC-04. 참여자가 일정을 수락하면 제안자에게 수락 알림을 생성한다.")
    void createScheduleResponseNotificationAccepted() {
        Schedule schedule = createSchedule();
        ScheduleParticipant participant = createParticipant(schedule, member2, ParticipantStatus.ACCEPTED);

        notificationService.createScheduleResponseNotification(schedule, participant);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();

        assertThat(saved.getMember()).isEqualTo(member);
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.SCHEDULE_RESPONSE);
        assertThat(saved.getTitle()).isEqualTo("일정 참여 응답이 도착했어요! 🎉");
        assertThat(saved.getBody()).isEqualTo("영희님이 '공원 산책' 일정 참여를 수락했어요.");
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.getIsRead()).isFalse();
        assertThat(saved.getReferenceId()).isEqualTo(schedule.getId());
    }

    @Test
    @DisplayName("SC-04. 참여자가 일정을 거절하면 제안자에게 거절 알림을 생성한다.")
    void createScheduleResponseNotificationDeclined() {
        Schedule schedule = createSchedule();
        ScheduleParticipant participant = createParticipant(schedule, member2, ParticipantStatus.DECLINED);

        notificationService.createScheduleResponseNotification(schedule, participant);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();

        assertThat(saved.getMember()).isEqualTo(member);
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.SCHEDULE_RESPONSE);
        assertThat(saved.getTitle()).isEqualTo("일정 참여 거절 응답이 도착했어요.");
        assertThat(saved.getBody()).isEqualTo("영희님이 '공원 산책' 일정 참여를 거절했어요.");
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.getIsRead()).isFalse();
        assertThat(saved.getReferenceId()).isEqualTo(schedule.getId());
    }

    @Test
    @DisplayName("SC-04. 제안자 본인의 응답에는 알림을 생성하지 않는다.")
    void createScheduleResponseNotificationExcludeProposer() {
        Schedule schedule = createSchedule();
        ScheduleParticipant participant = createParticipant(schedule, member, ParticipantStatus.ACCEPTED);

        notificationService.createScheduleResponseNotification(schedule, participant);

        verify(notificationRepository, never()).save(any(Notification.class));
    }


    @Test
    @DisplayName("SC-05. 기존 참여자에게 일정 변경 알림을 생성하고 제안자는 제외한다.")
    void createScheduleUpdateNotificationsChangedParticipants() {
        Schedule schedule = createSchedule();

        ScheduleParticipant proposer = createParticipant(schedule, member, ParticipantStatus.ACCEPTED);
        ScheduleParticipant existingParticipant = createParticipant(schedule, member2, ParticipantStatus.ACCEPTED);

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member2, NotificationType.SCHEDULE_CHANGED, schedule.getId(), schedule.getInviteVersion())).willReturn(false);

        notificationService.createScheduleUpdateNotifications(schedule, List.of(proposer, existingParticipant), List.of(), List.of());

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> saved = captor.getValue();

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getMember()).isEqualTo(member2);
        assertThat(saved.get(0).getNotificationType()).isEqualTo(NotificationType.SCHEDULE_CHANGED);
        assertThat(saved.get(0).getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.get(0).getReferenceId()).isEqualTo(schedule.getId());
        assertThat(saved.get(0).getReferenceVersion()).isEqualTo(schedule.getInviteVersion());

        verify(notificationRepository, never()).existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member, NotificationType.SCHEDULE_CHANGED, schedule.getId(), schedule.getInviteVersion());
    }

    @Test
    @DisplayName("SC-05. 일정에서 제외된 참여자에게 참여 제외 알림을 생성한다.")
    void createScheduleUpdateNotificationsRemovedParticipants() {
        Schedule schedule = createSchedule();

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member2, NotificationType.SCHEDULE_REMOVED, schedule.getId(), schedule.getInviteVersion())).willReturn(false);

        notificationService.createScheduleUpdateNotifications(schedule, List.of(), List.of(member2), List.of());

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> saved = captor.getValue();

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getMember()).isEqualTo(member2);
        assertThat(saved.get(0).getNotificationType()).isEqualTo(NotificationType.SCHEDULE_REMOVED);
        assertThat(saved.get(0).getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.get(0).getReferenceId()).isEqualTo(schedule.getId());
        assertThat(saved.get(0).getReferenceVersion()).isEqualTo(schedule.getInviteVersion());
    }

    @Test
    @DisplayName("SC-05. 새로 추가된 참여자에게 일정 초대 알림을 생성한다.")
    void createScheduleUpdateNotificationsAddedParticipants() {
        Schedule schedule = createSchedule();

        ScheduleParticipant addedParticipant = createParticipant(schedule, member2, ParticipantStatus.PENDING);

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member2, NotificationType.SCHEDULE_INVITATION, schedule.getId(), schedule.getInviteVersion())).willReturn(false);

        notificationService.createScheduleUpdateNotifications(schedule, List.of(), List.of(), List.of(addedParticipant));

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> saved = captor.getValue();

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getMember()).isEqualTo(member2);
        assertThat(saved.get(0).getNotificationType()).isEqualTo(NotificationType.SCHEDULE_INVITATION);
        assertThat(saved.get(0).getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.get(0).getReferenceId()).isEqualTo(schedule.getId());
        assertThat(saved.get(0).getReferenceVersion()).isEqualTo(schedule.getInviteVersion());
    }

    @Test
    @DisplayName("SC-05. 동일한 일정 버전의 변경 및 제외 알림이 이미 존재하면 중복 생성하지 않는다.")
    void createScheduleUpdateNotificationsPreventDuplicate() {
        Schedule schedule = createSchedule();

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member2, NotificationType.SCHEDULE_CHANGED, schedule.getId(), schedule.getInviteVersion())).willReturn(true);

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member3, NotificationType.SCHEDULE_REMOVED, schedule.getId(), schedule.getInviteVersion())).willReturn(true);

        notificationService.createScheduleUpdateNotifications(schedule, List.of(createParticipant(schedule, member2, ParticipantStatus.ACCEPTED)), List.of(member3), List.of());

        verify(notificationRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("SC-06. 일정 재확인이 필요한 참여자에게 리마인더 알림을 생성한다.")
    void createScheduleReconfirmReminderNotificationsSuccess() {
        Schedule schedule = createReconfirmSchedule(ScheduleStatus.PENDING_RESPONSE, NOW.plusHours(1), NOW.minusMinutes(1));

        ScheduleParticipant participant = createReconfirmParticipant(schedule, member2);

        given(scheduleRepository.findByIdForUpdate(schedule.getId())).willReturn(Optional.of(schedule));

        given(scheduleParticipantRepository
                .findAllReconfirmRequiredWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED))
                .willReturn(List.of(participant));

        given(notificationRepository
                .existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member2, NotificationType.SCHEDULE_RECONFIRM_REMINDER, schedule.getId(), schedule.getInviteVersion()))
                .willReturn(false);

        notificationService.createScheduleReconfirmReminderNotifications(schedule.getId(), NOW);

        ArgumentCaptor<Iterable<Notification>> captor = ArgumentCaptor.forClass(Iterable.class);

        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> saved = StreamSupport.stream(captor.getValue().spliterator(), false).toList();

        assertThat(saved).hasSize(1);

        Notification notification = saved.get(0);

        assertThat(notification.getMember()).isEqualTo(member2);
        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.SCHEDULE_RECONFIRM_REMINDER);
        assertThat(notification.getTitle()).isEqualTo("변경된 일정을 다시 확인해 주세요!");
        assertThat(notification.getBody()).isEqualTo("'공원 산책'의 일정이 바뀌었어요. 변경된 일정에 다시 수락 또는 거절해 주세요.");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getIsRead()).isFalse();
        assertThat(notification.getReferenceId()).isEqualTo(schedule.getId());
        assertThat(notification.getReferenceDate()).isEqualTo(schedule.getScheduleDate());
        assertThat(notification.getReferenceVersion()).isEqualTo(schedule.getInviteVersion());

        assertThat(schedule.getReconfirmReminderAt()).isNull();
    }

    @Test
    @DisplayName("SC-06. 이미 재수락하거나 거절한 참여자에게는 리마인더 알림을 생성하지 않는다.")
    void createScheduleReconfirmReminderNotificationsExcludeRespondedParticipants() {
        Schedule schedule = createReconfirmSchedule(ScheduleStatus.PENDING_RESPONSE, NOW.plusHours(1), NOW.minusMinutes(1));

        ScheduleParticipant reconfirmRequired = createReconfirmParticipant(schedule, member2);

        ScheduleParticipant alreadyAccepted = createParticipant(schedule, member3, ParticipantStatus.ACCEPTED);

        ScheduleParticipant declined = createParticipant(schedule, member, ParticipantStatus.DECLINED);

        given(scheduleRepository.findByIdForUpdate(schedule.getId())).willReturn(Optional.of(schedule));

        given(scheduleParticipantRepository.findAllReconfirmRequiredWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED)).willReturn(List.of(reconfirmRequired));

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member2, NotificationType.SCHEDULE_RECONFIRM_REMINDER, schedule.getId(), schedule.getInviteVersion())).willReturn(false);

        notificationService.createScheduleReconfirmReminderNotifications(schedule.getId(), NOW);

        ArgumentCaptor<Iterable<Notification>> captor = ArgumentCaptor.forClass(Iterable.class);

        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> saved = StreamSupport.stream(captor.getValue().spliterator(), false).toList();

        assertThat(saved)
                .extracting(notification -> notification.getMember().getId())
                .containsExactly(member2.getId());

        verify(scheduleParticipantRepository).findAllReconfirmRequiredWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED);
    }

    @Test
    @DisplayName("SC-06. 동일한 일정 변경 버전의 재확인 알림이 존재하면 중복 생성하지 않는다.")
    void createScheduleReconfirmReminderNotificationsPreventDuplicate() {
        Schedule schedule = createReconfirmSchedule(ScheduleStatus.PENDING_RESPONSE, NOW.plusHours(1), NOW.minusMinutes(1));

        ScheduleParticipant participant = createReconfirmParticipant(schedule, member2);

        given(scheduleRepository.findByIdForUpdate(schedule.getId())).willReturn(Optional.of(schedule));

        given(scheduleParticipantRepository.findAllReconfirmRequiredWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED)).willReturn(List.of(participant));

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member2, NotificationType.SCHEDULE_RECONFIRM_REMINDER, schedule.getId(), schedule.getInviteVersion())).willReturn(true);

        notificationService.createScheduleReconfirmReminderNotifications(schedule.getId(), NOW);

        verify(notificationRepository, never()).saveAll(anyList());

        assertThat(schedule.getReconfirmReminderAt()).isNull();
    }

    @Test
    @DisplayName("SC-06. 응답 마감 시간이 지난 일정은 재확인 알림을 생성하지 않는다.")
    void createScheduleReconfirmReminderNotificationsDeadlineExpired() {
        Schedule schedule = createReconfirmSchedule(ScheduleStatus.PENDING_RESPONSE, NOW.minusMinutes(1), NOW.minusMinutes(1));

        given(scheduleRepository.findByIdForUpdate(schedule.getId())).willReturn(Optional.of(schedule));

        notificationService.createScheduleReconfirmReminderNotifications(schedule.getId(), NOW);

        verify(scheduleParticipantRepository, never()).findAllReconfirmRequiredWithMemberByScheduleIdAndStatus(anyLong(), any());

        verify(notificationRepository, never()).saveAll(anyList());

        assertThat(schedule.getReconfirmReminderAt()).isNull();
    }

    @Test
    @DisplayName("SC-06. 취소된 일정은 재확인 알림을 생성하지 않는다.")
    void createScheduleReconfirmReminderNotificationsCancelled() {
        Schedule schedule = createReconfirmSchedule(ScheduleStatus.CANCELLED, NOW.plusHours(1), NOW.minusMinutes(1));

        given(scheduleRepository.findByIdForUpdate(schedule.getId())).willReturn(Optional.of(schedule));

        notificationService.createScheduleReconfirmReminderNotifications(schedule.getId(), NOW);

        verify(scheduleParticipantRepository, never()).findAllReconfirmRequiredWithMemberByScheduleIdAndStatus(anyLong(), any());

        verify(notificationRepository, never()).saveAll(anyList());

        assertThat(schedule.getReconfirmReminderAt()).isNull();
    }

    @Test
    @DisplayName("SC-06. 재확인 리마인드 발송 시각이 아직 되지 않았다면 알림을 생성하지 않는다.")
    void createScheduleReconfirmReminderNotificationsNotDue() {
        LocalDateTime reminderAt = NOW.plusMinutes(5);

        Schedule schedule = createReconfirmSchedule(ScheduleStatus.PENDING_RESPONSE, NOW.plusHours(1), reminderAt);

        given(scheduleRepository.findByIdForUpdate(schedule.getId())).willReturn(Optional.of(schedule));

        notificationService.createScheduleReconfirmReminderNotifications(schedule.getId(), NOW);

        verify(scheduleParticipantRepository, never()).findAllReconfirmRequiredWithMemberByScheduleIdAndStatus(anyLong(), any());

        verify(notificationRepository, never()).saveAll(anyList());

        assertThat(schedule.getReconfirmReminderAt()).isEqualTo(reminderAt);
    }

    @Test
    @DisplayName("SC-07. 일정 취소 시 참여자에게 취소 알림을 생성하고 기존 대기 알림을 취소한다.")
    void createScheduleCancelledNotificationsSuccess() {
        Schedule schedule = createSchedule();

        ScheduleParticipant proposer = createParticipant(schedule, member, ParticipantStatus.ACCEPTED);
        ScheduleParticipant participant = createParticipant(schedule, member2, ParticipantStatus.ACCEPTED);

        Notification pendingInvitation = Notification.builder()
                .member(member2)
                .notificationType(NotificationType.SCHEDULE_INVITATION)
                .title("일정 초대")
                .body("일정 초대 알림")
                .status(NotificationStatus.PENDING)
                .referenceId(schedule.getId())
                .build();

        Notification pendingReminder = Notification.builder()
                .member(member2)
                .notificationType(NotificationType.SCHEDULE_INVITATION_REMINDER)
                .title("일정 리마인더")
                .body("일정 리마인더 알림")
                .status(NotificationStatus.PENDING)
                .referenceId(schedule.getId())
                .build();

        given(notificationRepository.findAllByNotificationTypeInAndReferenceIdAndStatus(anyCollection(), eq(schedule.getId()), eq(NotificationStatus.PENDING))).willReturn(List.of(pendingInvitation, pendingReminder));

        given(scheduleParticipantRepository.findAllWithMemberByScheduleId(schedule.getId())).willReturn(List.of(proposer, participant));

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member2, NotificationType.SCHEDULE_CANCELLED, schedule.getId())).willReturn(false);

        notificationService.createScheduleCancelledNotifications(schedule);

        assertThat(pendingInvitation.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
        assertThat(pendingReminder.getStatus()).isEqualTo(NotificationStatus.CANCELLED);

        ArgumentCaptor<Iterable<Notification>> captor = ArgumentCaptor.forClass(Iterable.class);

        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> saved = StreamSupport.stream(captor.getValue().spliterator(), false).toList();

        assertThat(saved).hasSize(1);

        Notification notification = saved.get(0);

        assertThat(notification.getMember()).isEqualTo(member2);
        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.SCHEDULE_CANCELLED);
        assertThat(notification.getTitle()).isEqualTo("'공원 산책' 일정이 취소됐어요.");
        assertThat(notification.getBody()).isEqualTo("철수님이 11월 15일 일정을 취소했어요. 변경된 내용을 확인해 주세요.");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getIsRead()).isFalse();
        assertThat(notification.getReferenceId()).isEqualTo(schedule.getId());
        assertThat(notification.getReferenceDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("SC-07. 일정 제안자는 일정 취소 알림 대상에서 제외한다.")
    void createScheduleCancelledNotificationsExcludeProposer() {
        Schedule schedule = createSchedule();

        ScheduleParticipant proposer = createParticipant(schedule, member, ParticipantStatus.ACCEPTED);
        ScheduleParticipant participant = createParticipant(schedule, member2, ParticipantStatus.ACCEPTED);

        given(notificationRepository.findAllByNotificationTypeInAndReferenceIdAndStatus(anyCollection(), eq(schedule.getId()), eq(NotificationStatus.PENDING))).willReturn(List.of());

        given(scheduleParticipantRepository.findAllWithMemberByScheduleId(schedule.getId())).willReturn(List.of(proposer, participant));

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member2, NotificationType.SCHEDULE_CANCELLED, schedule.getId())).willReturn(false);

        notificationService.createScheduleCancelledNotifications(schedule);

        verify(notificationRepository, never()).existsByMemberAndNotificationTypeAndReferenceId(member, NotificationType.SCHEDULE_CANCELLED, schedule.getId());

        ArgumentCaptor<Iterable<Notification>> captor = ArgumentCaptor.forClass(Iterable.class);

        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> saved = StreamSupport.stream(captor.getValue().spliterator(), false).toList();

        assertThat(saved)
                .extracting(notification -> notification.getMember())
                .containsExactly(member2);
    }

    @Test
    @DisplayName("SC-07. 이미 일정 취소 알림이 존재하면 중복 생성하지 않는다.")
    void createScheduleCancelledNotificationsPreventDuplicate() {
        Schedule schedule = createSchedule();

        ScheduleParticipant participant = createParticipant(schedule, member2, ParticipantStatus.ACCEPTED);

        given(notificationRepository.findAllByNotificationTypeInAndReferenceIdAndStatus(anyCollection(), eq(schedule.getId()), eq(NotificationStatus.PENDING))).willReturn(List.of());

        given(scheduleParticipantRepository.findAllWithMemberByScheduleId(schedule.getId())).willReturn(List.of(participant));

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member2, NotificationType.SCHEDULE_CANCELLED, schedule.getId())).willReturn(true);

        notificationService.createScheduleCancelledNotifications(schedule);

        verify(notificationRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("SC-08 일정이 확정되지 않으면 제안자에게 알림을 생성한다")
    void createScheduleNotConfirmedNotificationSuccess() {
        Schedule schedule = createSchedule();

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member, NotificationType.SCHEDULE_NOT_CONFIRMED, schedule.getId())).willReturn(false);

        notificationService.createScheduleNotConfirmedNotification(schedule);

        then(notificationRepository).should().save(notificationCaptor.capture());

        Notification notification = notificationCaptor.getValue();

        assertThat(notification.getMember()).isEqualTo(member);
        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.SCHEDULE_NOT_CONFIRMED);
        assertThat(notification.getTitle()).isEqualTo("'공원 산책' 일정이 확정되지 않았어요.");
        assertThat(notification.getBody()).isEqualTo("응답 마감까지 참여자가 확정되지 않았어요. 일정 내용을 확인해 주세요.");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getIsRead()).isFalse();
        assertThat(notification.getReferenceId()).isEqualTo(schedule.getId());
        assertThat(notification.getReferenceDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("SC-08 동일한 일정의 미확정 알림은 중복 생성하지 않는다")
    void createScheduleNotConfirmedNotificationPreventDuplicate() {
        Schedule schedule = createSchedule();

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member, NotificationType.SCHEDULE_NOT_CONFIRMED, schedule.getId())).willReturn(true);

        notificationService.createScheduleNotConfirmedNotification(schedule);

        then(notificationRepository).should(never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("SC-08 제안자가 없는 일정은 알림을 생성하지 않는다")
    void createScheduleNotConfirmedNotificationWithoutProposer() {
        Schedule schedule = createSchedule();
        ReflectionTestUtils.setField(schedule, "proposer", null);

        notificationService.createScheduleNotConfirmedNotification(schedule);

        then(notificationRepository).should(never()).save(any(Notification.class));

        then(notificationRepository).should(never()).existsByMemberAndNotificationTypeAndReferenceId(any(Member.class), any(NotificationType.class), anyLong());
    }

    @Test
    @DisplayName("SC-09. 내일 일정이 확정된 제안자와 수락한 참여자에게 D-1 알림을 생성한다.")
    void createScheduleD1ReminderNotificationsSuccess() {
        LocalDate scheduleDate = TODAY.plusDays(1);
        Schedule schedule = createD1Schedule(scheduleDate, ScheduleStatus.CONFIRMED, 1);

        ScheduleParticipant accepted = createParticipant(schedule, member2, ParticipantStatus.ACCEPTED);
        ScheduleParticipant declined = createParticipant(schedule, member3, ParticipantStatus.DECLINED);

        given(scheduleRepository.findAllForD1Reminder(scheduleDate, ScheduleStatus.CONFIRMED, NOW)).willReturn(List.of(schedule));

        given(scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED)).willReturn(List.of(accepted));

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member, NotificationType.SCHEDULE_D1_REMINDER, schedule.getId(), 1)).willReturn(false);

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member2, NotificationType.SCHEDULE_D1_REMINDER, schedule.getId(), 1)).willReturn(false);

        notificationService.createScheduleD1ReminderNotifications(scheduleDate, NOW);

        ArgumentCaptor<Iterable<Notification>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> saved = StreamSupport.stream(captor.getValue().spliterator(), false).toList();

        assertThat(saved).hasSize(2);
        assertThat(saved)
                .extracting(Notification::getMember)
                .containsExactlyInAnyOrder(member, member2);

        assertThat(saved)
                .extracting(Notification::getNotificationType)
                .containsOnly(NotificationType.SCHEDULE_D1_REMINDER);

        assertThat(saved)
                .extracting(Notification::getTitle)
                .containsOnly("내일은 가족과 함께하는 날이에요!");

        assertThat(saved)
                .extracting(Notification::getBody)
                .containsOnly("내일 오후 2:00에 '공원 산책' 일정이 있어요. 미리 준비해 보세요.");

        assertThat(saved)
                .extracting(Notification::getStatus)
                .containsOnly(NotificationStatus.PENDING);

        assertThat(saved)
                .extracting(Notification::getIsRead)
                .containsOnly(false);

        assertThat(saved)
                .extracting(Notification::getReferenceId)
                .containsOnly(schedule.getId());

        assertThat(saved)
                .extracting(Notification::getReferenceDate)
                .containsOnly(scheduleDate);

        assertThat(saved)
                .extracting(Notification::getReferenceVersion)
                .containsOnly(1);

        verify(scheduleParticipantRepository).findAllConfirmedWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED);
    }

    @Test
    @DisplayName("SC-09. 동일한 일정 버전의 D-1 알림이 이미 존재하면 중복 생성하지 않는다.")
    void createScheduleD1ReminderNotificationsPreventDuplicate() {
        LocalDate scheduleDate = TODAY.plusDays(1);
        Schedule schedule = createD1Schedule(scheduleDate, ScheduleStatus.CONFIRMED, 1);

        ScheduleParticipant accepted = createParticipant(schedule, member2, ParticipantStatus.ACCEPTED);

        given(scheduleRepository.findAllForD1Reminder(scheduleDate, ScheduleStatus.CONFIRMED, NOW)).willReturn(List.of(schedule));

        given(scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED)).willReturn(List.of(accepted));

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member, NotificationType.SCHEDULE_D1_REMINDER, schedule.getId(), 1)).willReturn(true);

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member2, NotificationType.SCHEDULE_D1_REMINDER, schedule.getId(), 1)).willReturn(true);

        notificationService.createScheduleD1ReminderNotifications(scheduleDate, NOW);

        verify(notificationRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("SC-09. 내일 확정된 일정이 없으면 D-1 알림을 생성하지 않는다.")
    void createScheduleD1ReminderNotificationsNoSchedules() {
        LocalDate scheduleDate = TODAY.plusDays(1);

        given(scheduleRepository.findAllForD1Reminder(scheduleDate, ScheduleStatus.CONFIRMED, NOW)).willReturn(List.of());

        notificationService.createScheduleD1ReminderNotifications(scheduleDate, NOW);

        verify(scheduleParticipantRepository, never()).findAllConfirmedWithMemberByScheduleIdAndStatus(anyLong(), any());

        verify(notificationRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("SC-09. D-1 알림 발송 시각이 지난 후 일정이 확정되면 즉시 알림을 생성한다.")
    void createScheduleD1ReminderIfDueSuccess() {
        Schedule schedule = createD1Schedule(TODAY, ScheduleStatus.CONFIRMED, 2);

        ScheduleParticipant accepted = createParticipant(schedule, member2, ParticipantStatus.ACCEPTED);

        given(scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED)).willReturn(List.of(accepted));

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member, NotificationType.SCHEDULE_D1_REMINDER, schedule.getId(), 2)).willReturn(false);

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member2, NotificationType.SCHEDULE_D1_REMINDER, schedule.getId(), 2)).willReturn(false);

        notificationService.createScheduleD1ReminderIfDue(schedule, NOW);

        ArgumentCaptor<Iterable<Notification>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> saved = StreamSupport.stream(captor.getValue().spliterator(), false).toList();

        assertThat(saved).hasSize(2);
        assertThat(saved)
                .extracting(Notification::getMember)
                .containsExactlyInAnyOrder(member, member2);

        assertThat(saved)
                .extracting(Notification::getReferenceVersion)
                .containsOnly(2);
    }

    @Test
    @DisplayName("SC-09. 재확인이 필요한 참여자는 제외하고 제안자에게만 D-1 알림을 생성한다.")
    void createScheduleD1ReminderIfDueExcludesReconfirmRequired() {
        Schedule schedule = createD1Schedule(TODAY, ScheduleStatus.CONFIRMED, 2);

        ScheduleParticipant reconfirmRequired = createReconfirmParticipant(schedule, member2);

        given(scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED)).willReturn(List.of());

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member, NotificationType.SCHEDULE_D1_REMINDER, schedule.getId(), 2)).willReturn(false);

        notificationService.createScheduleD1ReminderIfDue(schedule, NOW);

        ArgumentCaptor<Iterable<Notification>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> saved = StreamSupport.stream(captor.getValue().spliterator(), false).toList();

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getMember()).isEqualTo(member);
        assertThat(saved.get(0).getNotificationType()).isEqualTo(NotificationType.SCHEDULE_D1_REMINDER);

        assertThat(saved)
                .extracting(Notification::getMember)
                .doesNotContain(member2);
    }

    @Test
    @DisplayName("SC-09. D-1 알림 발송 시각이 아직 되지 않았다면 즉시 알림을 생성하지 않는다.")
    void createScheduleD1ReminderIfDueNotDue() {
        Schedule schedule = createD1Schedule(TODAY.plusDays(1), ScheduleStatus.CONFIRMED, 2);

        notificationService.createScheduleD1ReminderIfDue(schedule, NOW);

        verify(scheduleParticipantRepository, never()).findAllConfirmedWithMemberByScheduleIdAndStatus(anyLong(), any());

        verify(notificationRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("SC-09. 일정의 대기 중인 D-1 알림을 취소한다.")
    void cancelScheduleD1RemindersSuccess() {
        Schedule schedule = createD1Schedule(TODAY.plusDays(1), ScheduleStatus.CONFIRMED, 1);

        Notification notification = Notification.builder()
                .member(member)
                .notificationType(NotificationType.SCHEDULE_D1_REMINDER)
                .title("내일은 가족과 함께하는 날이에요!")
                .body("내일 오후 2:00에 '공원 산책' 일정이 있어요. 미리 준비해 보세요.")
                .status(NotificationStatus.PENDING)
                .referenceId(schedule.getId())
                .referenceDate(schedule.getScheduleDate())
                .referenceVersion(schedule.getInviteVersion())
                .build();

        given(notificationRepository.findAllByNotificationTypeAndReferenceIdAndStatus(NotificationType.SCHEDULE_D1_REMINDER, schedule.getId(), NotificationStatus.PENDING)).willReturn(List.of(notification));

        notificationService.cancelScheduleD1Reminders(schedule.getId());

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
    }

    @Test
    @DisplayName("SC-09. 취소할 대기 중인 D-1 알림이 없으면 아무 작업도 하지 않는다.")
    void cancelScheduleD1RemindersNoNotification() {
        Long scheduleId = 300L;

        given(notificationRepository.findAllByNotificationTypeAndReferenceIdAndStatus(NotificationType.SCHEDULE_D1_REMINDER, scheduleId, NotificationStatus.PENDING)).willReturn(List.of());

        notificationService.cancelScheduleD1Reminders(scheduleId);

        verify(notificationRepository).findAllByNotificationTypeAndReferenceIdAndStatus(NotificationType.SCHEDULE_D1_REMINDER, scheduleId, NotificationStatus.PENDING);
    }

    @Test
    @DisplayName("SC-10. 일정 시작 2시간 전이 지난 확정 일정에 D0 알림을 생성한다.")
    void createScheduleD0ReminderNotificationsSuccess() {
        Schedule schedule = createD0Schedule(TODAY, LocalTime.of(11, 30), ScheduleStatus.CONFIRMED, 1);

        ScheduleParticipant accepted = createParticipant(schedule, member2, ParticipantStatus.ACCEPTED);

        given(scheduleRepository.findAllForD0Reminder(TODAY, TODAY.plusDays(1), ScheduleStatus.CONFIRMED)).willReturn(List.of(schedule));

        given(scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED)).willReturn(List.of(accepted));

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member, NotificationType.SCHEDULE_D0_REMINDER, schedule.getId(), 1)).willReturn(false);

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member2, NotificationType.SCHEDULE_D0_REMINDER, schedule.getId(), 1)).willReturn(false);

        notificationService.createScheduleD0ReminderNotifications(TODAY, TODAY.plusDays(1), NOW);

        ArgumentCaptor<Iterable<Notification>> captor = ArgumentCaptor.forClass(Iterable.class);

        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> saved = StreamSupport.stream(captor.getValue().spliterator(), false).toList();

        assertThat(saved).hasSize(2);

        assertThat(saved)
                .extracting(Notification::getMember)
                .containsExactlyInAnyOrder(member, member2);

        assertThat(saved)
                .extracting(Notification::getNotificationType)
                .containsOnly(NotificationType.SCHEDULE_D0_REMINDER);

        assertThat(saved)
                .extracting(Notification::getTitle)
                .containsOnly("오늘 가족과 함께할 시간이 다가와요!");

        assertThat(saved)
                .extracting(Notification::getBody)
                .containsOnly("'공원 산책' 일정이 2시간 후에 시작돼요. 즐거운 시간 보내세요!");

        assertThat(saved)
                .extracting(Notification::getStatus)
                .containsOnly(NotificationStatus.PENDING);

        assertThat(saved)
                .extracting(Notification::getIsRead)
                .containsOnly(false);

        assertThat(saved)
                .extracting(Notification::getReferenceId)
                .containsOnly(schedule.getId());

        assertThat(saved)
                .extracting(Notification::getReferenceVersion)
                .containsOnly(1);
    }

    @Test
    @DisplayName("SC-10. 2시간 이내에 생성된 일정도 D0 알림을 생성한다.")
    void createScheduleD0ReminderNotificationsRecentlyCreated() {
        Schedule schedule = createD0Schedule(TODAY, LocalTime.of(11, 30), ScheduleStatus.CONFIRMED, 1);

        given(scheduleRepository.findAllForD0Reminder(TODAY, TODAY.plusDays(1), ScheduleStatus.CONFIRMED)).willReturn(List.of(schedule));

        given(scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED)).willReturn(List.of());

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member, NotificationType.SCHEDULE_D0_REMINDER, schedule.getId(), 1)).willReturn(false);

        ReflectionTestUtils.setField(schedule, "createdAt", NOW.minusMinutes(10));

        notificationService.createScheduleD0ReminderNotifications(TODAY, TODAY.plusDays(1), NOW);

        ArgumentCaptor<Iterable<Notification>> captor = ArgumentCaptor.forClass(Iterable.class);

        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> saved = StreamSupport.stream(captor.getValue().spliterator(), false).toList();

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getMember()).isEqualTo(member);
        assertThat(saved.get(0).getNotificationType()).isEqualTo(NotificationType.SCHEDULE_D0_REMINDER);
    }

    @Test
    @DisplayName("SC-10. 이미 시작한 일정에는 D0 알림을 생성하지 않는다.")
    void createScheduleD0ReminderNotificationsAlreadyStarted() {
        Schedule schedule = createD0Schedule(TODAY, LocalTime.of(9, 0), ScheduleStatus.CONFIRMED, 1);

        given(scheduleRepository.findAllForD0Reminder(TODAY, TODAY.plusDays(1), ScheduleStatus.CONFIRMED)).willReturn(List.of(schedule));

        notificationService.createScheduleD0ReminderNotifications(TODAY, TODAY.plusDays(1), NOW);

        verify(scheduleParticipantRepository, never()).findAllConfirmedWithMemberByScheduleIdAndStatus(anyLong(), any());

        verify(notificationRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("SC-10. 동일한 일정 버전의 D0 알림이 이미 존재하면 중복 생성하지 않는다.")
    void createScheduleD0ReminderNotificationsPreventDuplicate() {
        Schedule schedule = createD0Schedule(TODAY, LocalTime.of(11, 30), ScheduleStatus.CONFIRMED ,1);

        ScheduleParticipant accepted = createParticipant(schedule, member2, ParticipantStatus.ACCEPTED);

        given(scheduleRepository.findAllForD0Reminder(TODAY, TODAY.plusDays(1), ScheduleStatus.CONFIRMED)).willReturn(List.of(schedule));

        given(scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED)).willReturn(List.of(accepted));

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member, NotificationType.SCHEDULE_D0_REMINDER, schedule.getId(), 1)).willReturn(true);

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member2, NotificationType.SCHEDULE_D0_REMINDER, schedule.getId(), 1)).willReturn(true);

        notificationService.createScheduleD0ReminderNotifications(TODAY, TODAY.plusDays(1), NOW);

        verify(notificationRepository, never()).saveAll(anyList());
    }


    @Test
    @DisplayName("SC-10. 일정의 PENDING D0 알림만 취소하고 SENT 알림은 유지한다.")
    void cancelScheduleD0RemindersSuccess() {
        Long scheduleId = 300L;

        Notification pendingNotification = Notification.builder()
                .member(member)
                .notificationType(NotificationType.SCHEDULE_D0_REMINDER)
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(scheduleId)
                .build();

        Notification sentNotification = Notification.builder()
                .member(member2)
                .notificationType(NotificationType.SCHEDULE_D0_REMINDER)
                .status(NotificationStatus.SENT)
                .isRead(false)
                .referenceId(scheduleId)
                .build();

        given(notificationRepository.findAllByNotificationTypeAndReferenceIdAndStatus(NotificationType.SCHEDULE_D0_REMINDER, scheduleId, NotificationStatus.PENDING)).willReturn(List.of(pendingNotification));

        notificationService.cancelScheduleD0Reminders(scheduleId);

        assertThat(pendingNotification.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
        assertThat(sentNotification.getStatus()).isEqualTo(NotificationStatus.SENT);

        then(notificationRepository).should().findAllByNotificationTypeAndReferenceIdAndStatus(NotificationType.SCHEDULE_D0_REMINDER, scheduleId, NotificationStatus.PENDING);
    }

    @Test
    @DisplayName("RC-01. 일정 시작 6시간 후 기록을 작성하지 않은 확정 참여자에게 알림을 생성한다.")
    void createRecordReminderNotificationsSuccess() {
        Schedule schedule = createRecordReminderSchedule(
                TODAY,
                LocalTime.of(10, 0),
                ScheduleStatus.CONFIRMED
        );

        ScheduleParticipant participant = createParticipant(
                schedule,
                member2,
                ParticipantStatus.ACCEPTED
        );

        LocalDateTime reminderTime = TODAY.atTime(16, 0);

        given(scheduleRepository.findAllForRecordReminder(
                TODAY.minusDays(2),
                TODAY,
                List.of(ScheduleStatus.CONFIRMED, ScheduleStatus.COMPLETED)
        )).willReturn(List.of(schedule));

        given(scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(
                schedule.getId(),
                ParticipantStatus.ACCEPTED
        )).willReturn(List.of(participant));

        given(reviewRepository.existsBySchedule_IdAndScheduleParticipant_Id(
                schedule.getId(),
                participant.getId()
        )).willReturn(false);

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(
                member2,
                NotificationType.RECORD,
                schedule.getId()
        )).willReturn(false);

        notificationService.createRecordReminderNotifications(reminderTime);

        ArgumentCaptor<Iterable<Notification>> captor =
                ArgumentCaptor.forClass(Iterable.class);

        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> saved = StreamSupport.stream(
                captor.getValue().spliterator(),
                false
        ).toList();

        assertThat(saved).hasSize(1);

        Notification notification = saved.get(0);

        assertThat(notification.getMember()).isEqualTo(member2);
        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.RECORD);
        assertThat(notification.getTitle()).isEqualTo("오늘 가족과 함께한 순간을 남겨보세요!");
        assertThat(notification.getBody()).isEqualTo(
                "'공원 산책' 활동은 어떠셨나요? 사진과 기록으로 오늘의 추억을 남겨보세요."
        );
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getIsRead()).isFalse();
        assertThat(notification.getReferenceId()).isEqualTo(schedule.getId());
        assertThat(notification.getReferenceDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("RC-01. 기록 알림 예정 시각이 22시 이후이면 다음 날 오전 9시에 알림을 생성한다.")
    void createRecordReminderNotificationsAfterQuietHours() {
        Schedule schedule = createRecordReminderSchedule(TODAY, LocalTime.of(16, 0), ScheduleStatus.CONFIRMED);

        ScheduleParticipant participant = createParticipant(schedule, member2, ParticipantStatus.ACCEPTED);

        LocalDateTime reminderTime = TODAY.plusDays(1).atTime(9, 0);

        given(scheduleRepository.findAllForRecordReminder(TODAY.minusDays(1), TODAY.plusDays(1), List.of(ScheduleStatus.CONFIRMED, ScheduleStatus.COMPLETED))).willReturn(List.of(schedule));

        given(scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED)).willReturn(List.of(participant));

        given(reviewRepository.existsBySchedule_IdAndScheduleParticipant_Id(schedule.getId(), participant.getId())).willReturn(false);

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member2, NotificationType.RECORD, schedule.getId())).willReturn(false);

        notificationService.createRecordReminderNotifications(reminderTime);

        verify(notificationRepository).saveAll(anyIterable());
    }

    @Test
    @DisplayName("RC-01. 기록을 이미 작성한 참여자에게는 알림을 생성하지 않는다.")
    void createRecordReminderNotificationsAlreadyReviewed() {
        Schedule schedule = createRecordReminderSchedule(TODAY, LocalTime.of(10, 0), ScheduleStatus.CONFIRMED);

        ScheduleParticipant participant = createParticipant(schedule, member2, ParticipantStatus.ACCEPTED);

        given(scheduleRepository.findAllForRecordReminder(TODAY.minusDays(2), TODAY, List.of(ScheduleStatus.CONFIRMED, ScheduleStatus.COMPLETED))).willReturn(List.of(schedule));

        given(scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED)).willReturn(List.of(participant));

        given(reviewRepository.existsBySchedule_IdAndScheduleParticipant_Id(schedule.getId(), participant.getId())).willReturn(true);

        notificationService.createRecordReminderNotifications(TODAY.atTime(16, 0));

        verify(notificationRepository, never()).saveAll(anyIterable());
    }

    @Test
    @DisplayName("RC-01. 이미 기록 알림이 생성된 참여자에게는 중복 알림을 생성하지 않는다.")
    void createRecordReminderNotificationsPreventDuplicate() {
        Schedule schedule = createRecordReminderSchedule(TODAY, LocalTime.of(10, 0), ScheduleStatus.CONFIRMED);

        ScheduleParticipant participant = createParticipant(schedule, member2, ParticipantStatus.ACCEPTED);

        given(scheduleRepository.findAllForRecordReminder(TODAY.minusDays(2), TODAY, List.of(ScheduleStatus.CONFIRMED, ScheduleStatus.COMPLETED))).willReturn(List.of(schedule));

        given(scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED)).willReturn(List.of(participant));

        given(reviewRepository.existsBySchedule_IdAndScheduleParticipant_Id(schedule.getId(), participant.getId())).willReturn(false);

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member2, NotificationType.RECORD, schedule.getId())).willReturn(true);

        notificationService.createRecordReminderNotifications(TODAY.atTime(16, 0));

        verify(notificationRepository, never()).saveAll(anyIterable());
    }

    @Test
    @DisplayName("RC-01. 기록 알림 대상 일정이 없으면 알림을 생성하지 않는다.")
    void createRecordReminderNotificationsNoSchedules() {
        given(scheduleRepository.findAllForRecordReminder(TODAY.minusDays(2), TODAY, List.of(ScheduleStatus.CONFIRMED, ScheduleStatus.COMPLETED))).willReturn(List.of());

        notificationService.createRecordReminderNotifications(TODAY.atTime(16, 0));

        verify(scheduleParticipantRepository, never()).findAllConfirmedWithMemberByScheduleIdAndStatus(anyLong(), any());

        verify(notificationRepository, never()).saveAll(anyIterable());
    }

    @Test
    @DisplayName("RC-02. 기록 작성 시 다른 확정 참여자에게 알림을 생성한다.")
    void createReviewCreatedNotificationsSuccess() {
        Schedule schedule = createSchedule();

        ScheduleParticipant writerParticipant = createParticipant(schedule, member, ParticipantStatus.ACCEPTED);
        ScheduleParticipant participant = createParticipant(schedule, member2, ParticipantStatus.ACCEPTED);

        given(scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED)).willReturn(List.of(writerParticipant, participant));

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member2, NotificationType.RECORD_CREATED, schedule.getId())).willReturn(false);

        notificationService.createReviewCreatedNotifications(schedule, writerParticipant);

        verify(notificationRepository).saveAll(notificationsCaptor.capture());

        List<Notification> saved = StreamSupport.stream(notificationsCaptor.getValue().spliterator(), false).toList();

        assertThat(saved).hasSize(1);

        Notification notification = saved.get(0);

        assertThat(notification.getMember()).isEqualTo(member2);
        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.RECORD_CREATED);
        assertThat(notification.getTitle()).isEqualTo("'철수'님이 오늘 미션에 추억을 남겼어요!");
        assertThat(notification.getBody()).isEqualTo("'공원 산책' 활동에 새로운 사진과 기록이 등록됐어요. 들어가서 확인해 보세요.");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getIsRead()).isFalse();
        assertThat(notification.getReferenceId()).isEqualTo(schedule.getId());
        assertThat(notification.getReferenceDate()).isEqualTo(schedule.getScheduleDate());
    }

    @Test
    @DisplayName("RC-02. 기록 작성자 본인에게는 알림을 생성하지 않는다.")
    void createReviewCreatedNotificationsExcludesWriter() {
        Schedule schedule = createSchedule();

        ScheduleParticipant writerParticipant = createParticipant(schedule, member, ParticipantStatus.ACCEPTED);

        given(scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED)).willReturn(List.of(writerParticipant));

        notificationService.createReviewCreatedNotifications(schedule, writerParticipant);

        verify(notificationRepository, never()).saveAll(any(Iterable.class));
    }

    @Test
    @DisplayName("RC-02. 탈퇴한 회원에게는 알림을 생성하지 않는다.")
    void createReviewCreatedNotificationsExcludesDeletedMember() {
        Schedule schedule = createSchedule();

        ScheduleParticipant writerParticipant = createParticipant(schedule, member, ParticipantStatus.ACCEPTED);

        ScheduleParticipant deletedParticipant = createParticipant(schedule, member3, ParticipantStatus.ACCEPTED);

        ReflectionTestUtils.setField(member3, "deletedAt", LocalDateTime.now());

        given(scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED)).willReturn(List.of(writerParticipant, deletedParticipant));

        notificationService.createReviewCreatedNotifications(schedule, writerParticipant);

        verify(notificationRepository, never()).saveAll(any(Iterable.class));
    }

    @Test
    @DisplayName("RC-02. 재확인이 필요한 참여자에게는 알림을 생성하지 않는다.")
    void createReviewCreatedNotificationsExcludesReconfirmRequiredParticipant() {
        Schedule schedule = createSchedule();

        ScheduleParticipant writerParticipant = createParticipant(schedule, member, ParticipantStatus.ACCEPTED);

        given(scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED)).willReturn(List.of(writerParticipant));

        notificationService.createReviewCreatedNotifications(schedule, writerParticipant);

        verify(notificationRepository, never()).saveAll(any(Iterable.class));
    }

    @Test
    @DisplayName("RC-02. 이미 기록 작성 알림이 존재하면 중복 생성하지 않는다.")
    void createReviewCreatedNotificationsDoesNotCreateDuplicate() {
        Schedule schedule = createSchedule();

        ScheduleParticipant writerParticipant = createParticipant(schedule, member, ParticipantStatus.ACCEPTED);

        ScheduleParticipant participant = createParticipant(schedule, member2, ParticipantStatus.ACCEPTED);

        given(scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED)).willReturn(List.of(writerParticipant, participant));

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member2, NotificationType.RECORD_CREATED, schedule.getId())).willReturn(true);

        notificationService.createReviewCreatedNotifications(schedule, writerParticipant);

        verify(notificationRepository, never()).saveAll(any(Iterable.class));
    }

    @Test
    @DisplayName("RC-02. 알림 대상자가 없으면 알림을 저장하지 않는다.")
    void createReviewCreatedNotificationsDoesNotSaveWhenNoRecipients() {
        Schedule schedule = createSchedule();

        ScheduleParticipant writerParticipant = createParticipant(schedule, member, ParticipantStatus.ACCEPTED);

        given(scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED)).willReturn(List.of(writerParticipant));

        notificationService.createReviewCreatedNotifications(schedule, writerParticipant);

        verify(notificationRepository, never()).saveAll(any(Iterable.class));
    }

    @Test
    @DisplayName("RC-03. 주간 미션 완료 시 작성자를 제외한 가족 구성원에게 알림을 생성한다.")
    void createWeeklyMissionCompletedNotificationsSuccess() {
        FamilyMissionRecommendation recommendation = createFamilyMissionRecommendation();

        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(member, member2, member3));

        notificationService.createWeeklyMissionCompletedNotifications(recommendation, member);

        verify(notificationRepository).saveAll(notificationsCaptor.capture());

        List<Notification> saved = StreamSupport.stream(notificationsCaptor.getValue().spliterator(), false).toList();

        assertThat(saved).hasSize(2);

        assertThat(saved).extracting(Notification::getMember).containsExactlyInAnyOrder(member2, member3);

        Notification notification = saved.get(0);

        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.WEEKLY_MISSION_COMPLETED);
        assertThat(notification.getTitle()).isEqualTo("이번 주 가족 미션을 완료했어요!");
        assertThat(notification.getBody()).isEqualTo("우리 가족이 '공원 산책' 미션을 완료했어요. 함께한 추억을 확인해 보세요.");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getIsRead()).isFalse();
        assertThat(notification.getReferenceId()).isEqualTo(recommendation.getId());
        assertThat(notification.getReferenceDate()).isEqualTo(recommendation.getStartedAt().toLocalDate());
    }

    @Test
    @DisplayName("RC-03. 작성자 외에 알림 대상자가 없으면 저장하지 않는다.")
    void createWeeklyMissionCompletedNotificationsNoRecipients() {
        FamilyMissionRecommendation recommendation = createFamilyMissionRecommendation();

        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(member));

        notificationService.createWeeklyMissionCompletedNotifications(recommendation, member);

        verify(notificationRepository, never()).saveAll(any(Iterable.class));
    }

    @Test
    @DisplayName("RC-03. 이미 주간 미션 완료 알림이 존재하면 중복 생성하지 않는다.")
    void createWeeklyMissionCompletedNotificationsDoesNotCreateDuplicate() {
        FamilyMissionRecommendation recommendation = createFamilyMissionRecommendation();

        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(member, member2));

        given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member2, NotificationType.WEEKLY_MISSION_COMPLETED, recommendation.getId())).willReturn(true);

        notificationService.createWeeklyMissionCompletedNotifications(recommendation, member);

        verify(notificationRepository, never()).saveAll(any(Iterable.class));
    }

    @Test
    @DisplayName("RC-03. 탈퇴한 회원은 주간 미션 완료 알림 대상에서 제외한다.")
    void createWeeklyMissionCompletedNotificationsExcludesDeletedMember() {
        FamilyMissionRecommendation recommendation = createFamilyMissionRecommendation();

        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(member, member2));

        notificationService.createWeeklyMissionCompletedNotifications(recommendation, member);

        verify(notificationRepository).saveAll(notificationsCaptor.capture());

        List<Notification> saved = StreamSupport.stream(notificationsCaptor.getValue().spliterator(), false).toList();

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getMember()).isEqualTo(member2);
    }

    @Test
    @DisplayName("RC-03. 활성 가족 구성원이 없으면 알림을 저장하지 않는다.")
    void createWeeklyMissionCompletedNotificationsEmptyMembers() {
        FamilyMissionRecommendation recommendation = createFamilyMissionRecommendation();

        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of());

        notificationService.createWeeklyMissionCompletedNotifications(recommendation, member);

        verify(notificationRepository, never()).saveAll(any(Iterable.class));
    }


    @DisplayName("EX-01 외부 행사 취소 알림")
    @Nested
    class ExternalEventCancelledNotificationTest {

        private final Long missionId = 500L;

        private Mission createMission() {
            Mission mission = mock(Mission.class);
            given(mission.getId()).willReturn(missionId);

            return mission;
        }

        private Schedule createSchedule(Long scheduleId, Member proposer) {
            Schedule schedule = mock(Schedule.class);
            given(schedule.getId()).willReturn(scheduleId);
            given(schedule.getProposer()).willReturn(proposer);

            return schedule;
        }

        private ScheduleParticipant createAcceptedParticipant(Member participant) {
            ScheduleParticipant scheduleParticipant = mock(ScheduleParticipant.class);
            given(scheduleParticipant.getMember()).willReturn(participant);

            return scheduleParticipant;
        }

        private List<Notification> captureNotifications() {
            ArgumentCaptor<Iterable<Notification>> captor = ArgumentCaptor.forClass(Iterable.class);

            then(notificationRepository).should().saveAll(captor.capture());

            return StreamSupport.stream(captor.getValue().spliterator(), false).toList();
        }

        @Test
        @DisplayName("행사 취소 시 일정 제안자와 확정 참여자에게 알림을 생성한다")
        void createExternalEventCancelledNotifications() {
            Mission mission = createMission();
            given(mission.getTitle()).willReturn("공원 산책");

            Schedule schedule = createSchedule(100L, member);

            ScheduleParticipant participant1 = createAcceptedParticipant(member2);
            ScheduleParticipant participant2 = createAcceptedParticipant(member3);

            given(scheduleRepository.findAllActiveByMissionId(eq(missionId), eq(List.of(ScheduleStatus.PENDING_RESPONSE, ScheduleStatus.CONFIRMED)))).willReturn(List.of(schedule));

            given(scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(100L, ParticipantStatus.ACCEPTED)).willReturn(List.of(participant1, participant2));

            given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(any(Member.class), eq(NotificationType.EXTERNAL_EVENT_CANCELLED), eq(missionId))).willReturn(false);

            notificationService.createExternalEventCancelledNotifications(mission);

            List<Notification> notifications = captureNotifications();

            assertThat(notifications).hasSize(3);
            assertThat(notifications)
                    .extracting(notification -> notification.getMember().getId())
                    .containsExactlyInAnyOrder(member.getId(), member2.getId(), member3.getId());

            assertThat(notifications)
                    .allSatisfy(notification -> {
                        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.EXTERNAL_EVENT_CANCELLED);
                        assertThat(notification.getTitle()).isEqualTo("예정된 활동에 변경 사항이 있어요.");
                        assertThat(notification.getBody()).isEqualTo("'공원 산책' 행사 취소가 확인됐어요. 방문 전에 일정 내용을 확인해 주세요.");
                        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
                        assertThat(notification.getReferenceId()).isEqualTo(missionId);
                    });
        }

        @Test
        @DisplayName("여러 일정에 동일한 회원이 포함되어 있어도 알림은 한 번만 생성한다")
        void createExternalEventCancelledNotificationsWithoutDuplicateRecipients() {
            Mission mission = createMission();
            given(mission.getTitle()).willReturn("공원 산책");

            Schedule schedule1 = createSchedule(100L, member);
            Schedule schedule2 = createSchedule(200L, member);

            ScheduleParticipant participant1 = createAcceptedParticipant(member2);
            ScheduleParticipant participant2 = createAcceptedParticipant(member2);
            ScheduleParticipant participant3 = createAcceptedParticipant(member3);

            given(scheduleRepository.findAllActiveByMissionId(eq(missionId), eq(List.of(ScheduleStatus.PENDING_RESPONSE, ScheduleStatus.CONFIRMED)))).willReturn(List.of(schedule1, schedule2));

            given(scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(100L, ParticipantStatus.ACCEPTED)).willReturn(List.of(participant1));

            given(scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(200L, ParticipantStatus.ACCEPTED)).willReturn(List.of(participant2, participant3));

            given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(any(Member.class), eq(NotificationType.EXTERNAL_EVENT_CANCELLED), eq(missionId))).willReturn(false);

            notificationService.createExternalEventCancelledNotifications(mission);

            List<Notification> notifications = captureNotifications();

            assertThat(notifications).hasSize(3);
            assertThat(notifications)
                    .extracting(notification -> notification.getMember().getId())
                    .containsExactlyInAnyOrder(member.getId(), member2.getId(), member3.getId());
        }

        @Test
        @DisplayName("이미 동일한 행사 취소 알림이 존재하면 중복 생성하지 않는다")
        void createExternalEventCancelledNotificationsWithoutExistingNotification() {
            Mission mission = createMission();

            Schedule schedule = createSchedule(100L, member);
            ScheduleParticipant participant = createAcceptedParticipant(member2);

            given(scheduleRepository.findAllActiveByMissionId(eq(missionId), eq(List.of(ScheduleStatus.PENDING_RESPONSE, ScheduleStatus.CONFIRMED)))).willReturn(List.of(schedule));

            given(scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(100L, ParticipantStatus.ACCEPTED)).willReturn(List.of(participant));

            given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(any(Member.class), eq(NotificationType.EXTERNAL_EVENT_CANCELLED), eq(missionId))).willReturn(true);

            notificationService.createExternalEventCancelledNotifications(mission);

            then(notificationRepository).should(never()).saveAll(any());
        }

        @Test
        @DisplayName("연결된 활성 일정이 없으면 알림을 생성하지 않는다")
        void createExternalEventCancelledNotificationsWithoutActiveSchedule() {
            Mission mission = createMission();

            given(scheduleRepository.findAllActiveByMissionId(eq(missionId), eq(List.of(ScheduleStatus.PENDING_RESPONSE, ScheduleStatus.CONFIRMED)))).willReturn(List.of());

            notificationService.createExternalEventCancelledNotifications(mission);

            then(scheduleParticipantRepository).shouldHaveNoInteractions();
            then(notificationRepository).should(never()).saveAll(any());
        }

        @Test
        @DisplayName("확정 참여자가 없어도 일정 제안자에게는 알림을 생성한다")
        void createExternalEventCancelledNotificationsToProposerOnly() {
            Mission mission = createMission();

            Schedule schedule = createSchedule(100L, member);

            given(scheduleRepository.findAllActiveByMissionId(eq(missionId), eq(List.of(ScheduleStatus.PENDING_RESPONSE, ScheduleStatus.CONFIRMED)))).willReturn(List.of(schedule));

            given(scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(100L, ParticipantStatus.ACCEPTED)).willReturn(List.of());

            given(notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(any(Member.class), eq(NotificationType.EXTERNAL_EVENT_CANCELLED), eq(missionId))).willReturn(false);

            notificationService.createExternalEventCancelledNotifications(mission);

            List<Notification> notifications = captureNotifications();

            assertThat(notifications).hasSize(1);
            assertThat(notifications.get(0).getMember().getId()).isEqualTo(member.getId());
        }
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
                .inviteVersion(1)
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

    private Schedule createReconfirmSchedule(ScheduleStatus status, LocalDateTime responseDeadline, LocalDateTime reconfirmReminderAt) {
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
                .status(status)
                .responseDeadline(responseDeadline)
                .inviteVersion(2)
                .reconfirmReminderAt(reconfirmReminderAt)
                .build();
    }

    private ScheduleParticipant createReconfirmParticipant(Schedule schedule, Member participant) {
        return ScheduleParticipant.builder()
                .schedule(schedule)
                .member(participant)
                .status(ParticipantStatus.ACCEPTED)
                .reconfirmRequired(true)
                .build();
    }

    private Schedule createD1Schedule(LocalDate scheduleDate, ScheduleStatus status, Integer inviteVersion) {
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
                .scheduleDate(scheduleDate)
                .scheduleTime(LocalTime.of(14, 0))
                .status(status)
                .inviteVersion(inviteVersion)
                .build();
    }

    private Schedule createD0Schedule(LocalDate scheduleDate, LocalTime scheduleTime, ScheduleStatus status, Integer inviteVersion) {
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
                .scheduleDate(scheduleDate)
                .scheduleTime(scheduleTime)
                .status(status)
                .inviteVersion(inviteVersion)
                .build();
    }

    private Schedule createRecordReminderSchedule(LocalDate scheduleDate, LocalTime scheduleTime, ScheduleStatus status) {
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
                .scheduleDate(scheduleDate)
                .scheduleTime(scheduleTime)
                .status(status)
                .inviteVersion(1)
                .build();
    }

    private FamilyMissionRecommendation createFamilyMissionRecommendation() {
        Mission mission = Mission.builder()
                .id(100L)
                .title("공원 산책")
                .build();

        return FamilyMissionRecommendation.builder()
                .id(500L)
                .family(family)
                .mission(mission)
                .startedAt(TODAY.with(DayOfWeek.MONDAY).atStartOfDay())
                .endedAt(TODAY.with(DayOfWeek.MONDAY).plusWeeks(1).atStartOfDay())
                .build();
    }
}