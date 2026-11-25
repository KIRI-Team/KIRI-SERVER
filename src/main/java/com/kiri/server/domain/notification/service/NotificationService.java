package com.kiri.server.domain.notification.service;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.entity.FamilyMissionRecommendation;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.notification.converter.NotificationConverter;
import com.kiri.server.domain.notification.dto.NotificationResDTO;
import com.kiri.server.domain.notification.entity.Notification;
import com.kiri.server.domain.notification.enums.NotificationPriority;
import com.kiri.server.domain.notification.enums.NotificationStatus;
import com.kiri.server.domain.notification.enums.NotificationType;
import com.kiri.server.domain.notification.repository.NotificationRepository;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import com.kiri.server.domain.question.exception.QuestionException;
import com.kiri.server.domain.question.exception.code.QuestionErrorCode;
import com.kiri.server.domain.question.repository.FamilyQuestionRepository;
import com.kiri.server.domain.question.repository.MemberAnswerRepository;
import com.kiri.server.domain.review.repository.ReviewRepository;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.domain.schedule.exception.ScheduleException;
import com.kiri.server.domain.schedule.exception.code.ScheduleErrorCode;
import com.kiri.server.domain.schedule.repository.ScheduleParticipantRepository;
import com.kiri.server.domain.schedule.repository.ScheduleRepository;
import com.kiri.server.domain.setting.entity.MemberSetting;
import com.kiri.server.domain.setting.repository.MemberSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final int NOTIFICATION_BATCH_SIZE = 100;
    private static final int PROCESSING_TIMEOUT_MINUTES = 5;
    private static final int MAX_DAILY_PUSH_COUNT = 2;

    private final FamilyQuestionRepository familyQuestionRepository;
    private final MemberRepository memberRepository;
    private final MemberSettingRepository memberSettingRepository;
    private final MemberAnswerRepository memberAnswerRepository;
    private final NotificationRepository notificationRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleParticipantRepository scheduleParticipantRepository;
    private final ReviewRepository reviewRepository;

    @Transactional
    public void createQuestionReminderNotifications(LocalDate today, LocalTime currentTime) {
        List<FamilyQuestion> familyQuestions = familyQuestionRepository.findAllByQuestionDate(today);

        for (FamilyQuestion familyQuestion : familyQuestions) {
            List<Member> members = memberRepository.findAllByFamilyAndDeletedAtIsNull(familyQuestion.getFamily());

            for (Member member : members) {
                createQuestionReminderIfNeeded(member, familyQuestion, currentTime);
            }
        }
    }

    private void createQuestionReminderIfNeeded(Member member, FamilyQuestion familyQuestion, LocalTime currentTime) {
        MemberSetting setting = memberSettingRepository.findByMember(member).orElse(null);

        if (setting == null) {
            log.warn("회원 알림 설정이 존재하지 않습니다. memberId={}", member.getId());
            return;
        }

        if (!setting.getRegularNotificationTime().equals(currentTime)) {
            return;
        }

        if (memberAnswerRepository.existsByFamilyQuestionAndMember(familyQuestion, member)) {
            return;
        }

        if (notificationRepository.existsByMemberAndNotificationTypeAndReferenceDate(member, NotificationType.QUESTION_REMINDER, familyQuestion.getQuestionDate())) {
            return;
        }

        Notification notification = NotificationConverter.toQuestionReminder(member, familyQuestion);

        notificationRepository.save(notification);
    }

    @Transactional
    public void cancelQuestionReminder(Member member, FamilyQuestion familyQuestion) {
        List<Notification> notifications = notificationRepository.findAllByMemberAndNotificationTypeAndReferenceIdAndStatus(member, NotificationType.QUESTION_REMINDER, familyQuestion.getId(), NotificationStatus.PENDING);

        notifications.forEach(Notification::cancel);
    }

    @Transactional
    public void createFamilyAnswerCompleteNotifications(Member lastAnswerer, FamilyQuestion familyQuestion) {
        List<Member> members = memberRepository.findAllByFamilyAndDeletedAtIsNull(familyQuestion.getFamily());

        if (members.isEmpty()) {
            return;
        }

        Set<Long> answeredMemberIds = memberAnswerRepository
                .findAllByFamilyQuestion(familyQuestion)
                .stream()
                .map(answer -> answer.getMember().getId())
                .collect(Collectors.toSet());

        boolean allAnswered = members.stream().allMatch(member -> answeredMemberIds.contains(member.getId()));

        if (!allAnswered) {
            return;
        }

        List<Notification> notifications = members.stream()
                .filter(member -> !member.getId().equals(lastAnswerer.getId()))
                .filter(member -> !notificationRepository.existsByMemberAndNotificationTypeAndReferenceDate(member, NotificationType.QUESTION_COMPLETE, familyQuestion.getQuestionDate()))
                .map(member -> NotificationConverter.toFamilyAnswerComplete(member, familyQuestion))
                .toList();

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
        }
    }

    @Transactional
    public void createPokeNotification(Member sender, Member receiver, LocalDate today) {
        FamilyQuestion familyQuestion = familyQuestionRepository.findByFamilyAndQuestionDate(receiver.getFamily(), today).orElseThrow(() -> new QuestionException(QuestionErrorCode.TODAY_QUESTION_NOT_FOUND));

        boolean hasAnswered = memberAnswerRepository.existsByFamilyQuestionAndMember(familyQuestion, receiver);

        Notification notification = NotificationConverter.toPoke(receiver, sender, familyQuestion, hasAnswered);

        notificationRepository.save(notification);
    }

    @Transactional
    public void createWeeklyMissionNotifications(Family family, Mission mission, LocalDate referenceDate) {
        List<Member> members = memberRepository.findAllByFamilyAndDeletedAtIsNull(family);

        if (members.isEmpty()) {
            return;
        }

        List<Notification> notifications = members.stream()
                .map(member -> NotificationConverter.toWeeklyMission(member, mission, referenceDate))
                .toList();

        notificationRepository.saveAll(notifications);
    }

    @Transactional
    public void createMissionChangedNotifications(Family family, Mission mission, LocalDate referenceDate) {
        List<Member> members = memberRepository.findAllByFamilyAndDeletedAtIsNull(family);

        if (members.isEmpty()) {
            return;
        }

        List<Notification> notifications = members.stream().map(member -> NotificationConverter.toMissionChanged(member, mission, referenceDate)).toList();

        notificationRepository.saveAll(notifications);
    }

    @Transactional
    public void createScheduleInvitationNotifications(Schedule schedule, List<ScheduleParticipant> participants) {
        List<Notification> notifications = participants.stream()
                .filter(participant -> participant.getStatus() == ParticipantStatus.PENDING)
                .filter(participant -> participant.getMember() != null)
                .map(participant -> NotificationConverter.toScheduleInvitation(participant.getMember(), schedule))
                .filter(notification -> !notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(notification.getMember(), NotificationType.SCHEDULE_INVITATION, schedule.getId(), schedule.getInviteVersion()))
                .toList();

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
        }
    }

    @Transactional
    public void cancelScheduleInvitationReminders(Long scheduleId) {
        List<Notification> notifications = notificationRepository.findAllByNotificationTypeAndReferenceIdAndStatus(NotificationType.SCHEDULE_INVITATION_REMINDER, scheduleId, NotificationStatus.PENDING);

        notifications.forEach(Notification::cancel);
    }

    @Transactional
    public void createScheduleInvitationReminderNotifications(Long scheduleId, LocalDateTime now) {
        Schedule schedule = scheduleRepository.findByIdForUpdate(scheduleId).orElseThrow(() -> new ScheduleException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));

        if (schedule == null) {
            log.warn("일정 초대 리마인더 대상 일정을 찾을 수 없습니다. scheduleId={}", scheduleId);
            return;
        }

        LocalDateTime reminderAt = schedule.getResponseReminderAt();

        if (reminderAt == null || reminderAt.isAfter(now)) {
            return;
        }

        if (schedule.getStatus() != ScheduleStatus.PENDING_RESPONSE || !schedule.getResponseDeadline().isAfter(now)) {
            schedule.clearResponseReminderAt();
            return;
        }

        List<ScheduleParticipant> participants = scheduleParticipantRepository.findAllWithMemberByScheduleIdAndStatus(scheduleId, ParticipantStatus.PENDING);

        List<Notification> notifications = participants.stream()
                .filter(participant -> participant.getMember() != null)
                .map(participant -> NotificationConverter.toScheduleInvitationReminder(participant.getMember(), schedule))
                .filter(notification -> !notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(notification.getMember(), NotificationType.SCHEDULE_INVITATION_REMINDER, scheduleId, schedule.getInviteVersion()))
                .toList();

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
        }

        schedule.clearResponseReminderAt();

        log.info("일정 초대 리마인더 생성 완료 scheduleId={}, notificationCount={}", scheduleId, notifications.size());
    }

    @Transactional
    public void createScheduleConfirmedNotifications(Schedule schedule) {
        Map<Long, Member> receivers = new LinkedHashMap<>();

        Member proposer = schedule.getProposer();

        if (proposer != null) {
            receivers.put(proposer.getId(), proposer);
        }

        List<ScheduleParticipant> participants = scheduleParticipantRepository.findAllWithMemberByScheduleId(schedule.getId());

        participants.stream()
                .filter(participant -> participant.getStatus() == ParticipantStatus.ACCEPTED)
                .filter(participant -> participant.getMember() != null)
                .forEach(participant -> receivers.putIfAbsent(participant.getMember().getId(), participant.getMember()));

        List<Notification> notifications = receivers.values().stream()
                .filter(member -> !notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member, NotificationType.SCHEDULE, schedule.getId()))
                .map(member -> NotificationConverter.toScheduleConfirmed(member, schedule))
                .toList();

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
        }

        log.info("일정 확정 알림 생성 완료 scheduleId={}, notificationCount={}", schedule.getId(), notifications.size());
    }

    @Transactional
    public void createScheduleResponseNotification(Schedule schedule, ScheduleParticipant participant) {
        Member proposer = schedule.getProposer();
        Member responder = participant.getMember();

        if (proposer == null || responder == null) {
            return;
        }

        if (proposer.getId().equals(responder.getId())) {
            return;
        }

        Notification notification = NotificationConverter.toScheduleResponse(proposer, responder, schedule, participant.getStatus());

        notificationRepository.save(notification);

        log.info("일정 참여 응답 알림 생성 완료 scheduleId={}, responderId={}, response={}", schedule.getId(), responder.getId(), participant.getStatus());
    }

    @Transactional
    public void createScheduleUpdateNotifications(Schedule schedule, List<ScheduleParticipant> existingParticipants, List<Member> removedMembers, List<ScheduleParticipant> addedParticipants) {
        Long scheduleId = schedule.getId();
        Integer inviteVersion = schedule.getInviteVersion();
        Member proposer = schedule.getProposer();

        Map<Long, Member> changedReceivers = new LinkedHashMap<>();

        existingParticipants.stream()
                .filter(participant -> participant.getMember() != null)
                .filter(participant -> !participant.getMember().getId().equals(proposer.getId()))
                .forEach(participant -> changedReceivers.putIfAbsent(participant.getMember().getId(), participant.getMember()));

        List<Notification> changedNotifications = changedReceivers.values().stream()
                .filter(member -> !notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member, NotificationType.SCHEDULE_CHANGED, scheduleId, inviteVersion))
                .map(member -> NotificationConverter.toScheduleChanged(member, schedule))
                .toList();

        Map<Long, Member> removedReceivers = new LinkedHashMap<>();

        removedMembers.stream()
                .filter(member -> member != null)
                .filter(member -> !member.getId().equals(proposer.getId()))
                .forEach(member -> removedReceivers.putIfAbsent(member.getId(), member));

        List<Notification> removedNotifications = removedReceivers.values().stream()
                .filter(member -> !notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(member, NotificationType.SCHEDULE_REMOVED, scheduleId, inviteVersion))
                .map(member -> NotificationConverter.toScheduleRemoved(member, schedule))
                .toList();

        if (!changedNotifications.isEmpty()) {
            notificationRepository.saveAll(changedNotifications);
        }

        if (!removedNotifications.isEmpty()) {
            notificationRepository.saveAll(removedNotifications);
        }

        createScheduleInvitationNotifications(schedule, addedParticipants);

        log.info("일정 변경 알림 생성 완료 scheduleId={}, changedCount={}, removedCount={}, addedCount={}", scheduleId, changedNotifications.size(), removedNotifications.size(), addedParticipants.stream().filter(participant -> participant.getStatus() == ParticipantStatus.PENDING).count());
    }

    @Transactional
    public void createScheduleReconfirmReminderNotifications(Long scheduleId, LocalDateTime now) {
        Schedule schedule = scheduleRepository.findByIdForUpdate(scheduleId).orElse(null);

        if (schedule == null) {
            return;
        }

        LocalDateTime reconfirmReminderAt = schedule.getReconfirmReminderAt();

        if (reconfirmReminderAt == null || reconfirmReminderAt.isAfter(now)) {
            return;
        }

        if (!schedule.isResponseOpen(now)) {
            schedule.clearReconfirmReminderAt();
            return;
        }

        List<ScheduleParticipant> participants = scheduleParticipantRepository.findAllReconfirmRequiredWithMemberByScheduleIdAndStatus(scheduleId, ParticipantStatus.ACCEPTED);

        List<Notification> notifications = participants.stream()
                .map(participant -> NotificationConverter.toScheduleReconfirmReminder(participant.getMember(), schedule))
                .filter(notification -> !notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(notification.getMember(), NotificationType.SCHEDULE_RECONFIRM_REMINDER, scheduleId, schedule.getInviteVersion()))
                .toList();

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
        }

        schedule.clearReconfirmReminderAt();

        log.info("일정 재확인 리마인드 생성 완료 scheduleId={}, inviteVersion={}, notificationCount={}", scheduleId, schedule.getInviteVersion(), notifications.size());
    }

    @Transactional
    public void createScheduleCancelledNotifications(Schedule schedule) {
        Long scheduleId = schedule.getId();
        Long proposerId = schedule.getProposer().getId();

        cancelPendingScheduleNotifications(scheduleId);

        List<ScheduleParticipant> participants = scheduleParticipantRepository.findAllWithMemberByScheduleId(scheduleId);

        Map<Long, Member> receivers = new LinkedHashMap<>();

        participants.stream()
                .filter(participant -> participant.getMember() != null)
                .map(ScheduleParticipant::getMember)
                .filter(member -> !member.getId().equals(proposerId))
                .filter(member -> !member.isWithdrawn())
                .forEach(member -> receivers.putIfAbsent(member.getId(), member));

        List<Notification> notifications = receivers.values().stream()
                .map(member -> NotificationConverter.toScheduleCancelled(member, schedule))
                .filter(notification -> !notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(notification.getMember(), NotificationType.SCHEDULE_CANCELLED, scheduleId))
                .toList();

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
        }
    }

    @Transactional
    public void createScheduleCancelledByWithdrawalNotifications(Schedule schedule) {
        Long scheduleId = schedule.getId();

        cancelPendingScheduleNotifications(scheduleId);

        Map<Long, Member> receivers = new LinkedHashMap<>();

        Member proposer = schedule.getProposer();

        if (proposer != null) {
            receivers.put(proposer.getId(), proposer);
        }

        scheduleParticipantRepository.findAllWithMemberByScheduleId(scheduleId).stream()
                .map(ScheduleParticipant::getMember)
                .filter(Objects::nonNull)
                .forEach(member -> receivers.putIfAbsent(member.getId(), member));

        List<Notification> notifications = receivers.values().stream()
                .filter(member -> !member.isWithdrawn())
                .filter(member -> !notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member, NotificationType.SCHEDULE_CANCELLED, scheduleId))
                .map(member -> NotificationConverter.toScheduleCancelledByWithdrawal(member, schedule))
                .toList();

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
        }

        log.info("탈퇴로 인한 일정 취소 알림 생성 완료 scheduleId={}, notificationCount={}", scheduleId, notifications.size());
    }

    @Transactional
    public void createScheduleParticipantWithdrawnNotifications(Schedule schedule, List<ScheduleParticipant> remainingParticipants, boolean confirmationLost) {
        Map<Long, Member> receivers = new LinkedHashMap<>();

        Member proposer = schedule.getProposer();

        if (proposer != null) {
            receivers.put(proposer.getId(), proposer);
        }

        remainingParticipants.stream()
                .map(ScheduleParticipant::getMember)
                .filter(Objects::nonNull)
                .forEach(member -> receivers.putIfAbsent(member.getId(), member));

        List<Notification> notifications = receivers.values().stream()
                .filter(member -> !member.isWithdrawn())
                .map(member -> NotificationConverter.toScheduleParticipantWithdrawn(member, schedule, confirmationLost))
                .toList();

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
        }

        log.info("탈퇴로 인한 일정 참여 인원 변경 알림 생성 완료 scheduleId={}, confirmationLost={}, notificationCount={}", schedule.getId(), confirmationLost, notifications.size());
    }

    private void cancelPendingScheduleNotifications(Long scheduleId) {
        List<NotificationType> scheduleNotificationTypes = List.of(NotificationType.SCHEDULE, NotificationType.SCHEDULE_INVITATION, NotificationType.SCHEDULE_INVITATION_REMINDER, NotificationType.SCHEDULE_RESPONSE, NotificationType.SCHEDULE_CHANGED, NotificationType.SCHEDULE_REMOVED, NotificationType.SCHEDULE_RECONFIRM_REMINDER, NotificationType.SCHEDULE_D1_REMINDER, NotificationType.SCHEDULE_D0_REMINDER);

        List<Notification> pendingNotifications = notificationRepository.findAllByNotificationTypeInAndReferenceIdAndStatus(scheduleNotificationTypes, scheduleId, NotificationStatus.PENDING);

        pendingNotifications.forEach(Notification::cancel);
    }

    @Transactional
    public void createScheduleNotConfirmedNotification(Schedule schedule) {
        Member proposer = schedule.getProposer();

        if (proposer == null) {
            return;
        }

        boolean alreadyExists = notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(proposer, NotificationType.SCHEDULE_NOT_CONFIRMED, schedule.getId());

        if (alreadyExists) {
            return;
        }

        Notification notification = NotificationConverter.toScheduleNotConfirmed(proposer, schedule);

        notificationRepository.save(notification);
    }

    @Transactional
    public void createScheduleD1ReminderNotifications(LocalDate scheduleDate, LocalDateTime createdBefore) {
        List<Schedule> schedules = scheduleRepository.findAllForD1Reminder(scheduleDate, ScheduleStatus.CONFIRMED, createdBefore);

        for (Schedule schedule : schedules) {
            Map<Long, Member> receivers = new LinkedHashMap<>();

            Member proposer = schedule.getProposer();
            if (proposer != null) {
                receivers.put(proposer.getId(), proposer);
            }

            List<ScheduleParticipant> participants = scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED);

            participants.stream()
                    .filter(participant -> participant.getMember() != null)
                    .map(ScheduleParticipant::getMember)
                    .forEach(member -> receivers.putIfAbsent(member.getId(), member));

            List<Notification> notifications = receivers.values().stream()
                    .map(member -> NotificationConverter.toScheduleD1Reminder(member, schedule))
                    .filter(notification -> !notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(notification.getMember(), NotificationType.SCHEDULE_D1_REMINDER, schedule.getId(), schedule.getInviteVersion()))
                    .toList();

            if (!notifications.isEmpty()) {
                notificationRepository.saveAll(notifications);
            }
        }
    }

    @Transactional
    public void cancelScheduleD1Reminders(Long scheduleId) {
        List<Notification> notifications = notificationRepository.findAllByNotificationTypeAndReferenceIdAndStatus(NotificationType.SCHEDULE_D1_REMINDER, scheduleId, NotificationStatus.PENDING);

        notifications.forEach(Notification::cancel);
    }

    @Transactional
    public void createScheduleD1ReminderIfDue(Schedule schedule, LocalDateTime now) {
        if (schedule.getStatus() != ScheduleStatus.CONFIRMED) {
            return;
        }

        LocalDateTime reminderAt = schedule.getScheduleDate().minusDays(1).atTime(19, 0);

        if (now.isBefore(reminderAt)) {
            return;
        }

        if (schedule.getScheduleDate().isBefore(now.toLocalDate())) {
            return;
        }

        Map<Long, Member> receivers = new LinkedHashMap<>();

        Member proposer = schedule.getProposer();
        if (proposer != null) {
            receivers.put(proposer.getId(), proposer);
        }

        List<ScheduleParticipant> participants = scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED);

        participants.stream()
                .filter(p -> p.getMember() != null)
                .map(ScheduleParticipant::getMember)
                .forEach(member -> receivers.putIfAbsent(member.getId(), member));

        List<Notification> notifications = receivers.values().stream()
                .map(member -> NotificationConverter.toScheduleD1Reminder(member, schedule))
                .filter(notification -> !notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(notification.getMember(), NotificationType.SCHEDULE_D1_REMINDER, schedule.getId(), schedule.getInviteVersion()))
                .toList();

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
        }
    }

    @Transactional
    public void createScheduleD0ReminderNotifications(LocalDate startDate, LocalDate endDate, LocalDateTime now) {
        List<Schedule> schedules = scheduleRepository.findAllForD0Reminder(startDate, endDate, ScheduleStatus.CONFIRMED);

        for (Schedule schedule : schedules) {
            LocalDateTime scheduleDateTime = LocalDateTime.of(schedule.getScheduleDate(), schedule.getScheduleTime());

            LocalDateTime reminderAt = scheduleDateTime.minusHours(2);

            if (now.isBefore(reminderAt) || !now.isBefore(scheduleDateTime)) {
                continue;
            }

            Map<Long, Member> receivers = new LinkedHashMap<>();

            Member proposer = schedule.getProposer();

            if (proposer != null) {
                receivers.put(proposer.getId(), proposer);
            }

            List<ScheduleParticipant> participants = scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED);

            participants.stream()
                    .filter(participant -> participant.getMember() != null)
                    .map(ScheduleParticipant::getMember)
                    .forEach(member -> receivers.putIfAbsent(member.getId(), member));

            List<Notification> notifications = receivers.values().stream()
                    .map(member -> NotificationConverter.toScheduleD0Reminder(member, schedule))
                    .filter(notification -> !notificationRepository.existsByMemberAndNotificationTypeAndReferenceIdAndReferenceVersion(notification.getMember(),NotificationType.SCHEDULE_D0_REMINDER, schedule.getId(), schedule.getInviteVersion()))
                    .toList();

            if (!notifications.isEmpty()) {
                notificationRepository.saveAll(notifications);
            }

            log.info("일정 당일 리마인더 생성 완료 scheduleId={}, notificationCount={}", schedule.getId(), notifications.size());
        }
    }

    @Transactional
    public void cancelScheduleD0Reminders(Long scheduleId) {
        List<Notification> notifications = notificationRepository.findAllByNotificationTypeAndReferenceIdAndStatus(NotificationType.SCHEDULE_D0_REMINDER, scheduleId, NotificationStatus.PENDING);

        notifications.forEach(Notification::cancel);
    }

    @Transactional
    public void createRecordReminderNotifications(LocalDateTime now) {
        List<Schedule> schedules = scheduleRepository.findAllForRecordReminder(now.toLocalDate().minusDays(2), now.toLocalDate(), List.of(ScheduleStatus.CONFIRMED, ScheduleStatus.COMPLETED));

        for (Schedule schedule : schedules) {
            LocalDateTime scheduleStartAt = LocalDateTime.of(schedule.getScheduleDate(), schedule.getScheduleTime());

            LocalDateTime reminderAt = calculateRecordReminderAt(scheduleStartAt);

            if (reminderAt.isAfter(now)) {
                continue;
            }

            List<ScheduleParticipant> participants = scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED);

            List<Notification> notifications = participants.stream()
                    .filter(participant -> participant.getMember() != null)
                    .filter(participant -> participant.getMember().getDeletedAt() == null)
                    .filter(participant -> !reviewRepository.existsBySchedule_IdAndScheduleParticipant_Id(schedule.getId(), participant.getId()))
                    .filter(participant -> !notificationRepository
                            .existsByMemberAndNotificationTypeAndReferenceId(participant.getMember(), NotificationType.RECORD, schedule.getId()))
                    .map(participant -> NotificationConverter.toRecordReminder(participant.getMember(), schedule))
                    .toList();

            if (!notifications.isEmpty()) {
                notificationRepository.saveAll(notifications);
            }

            log.info("활동 기록 알림 생성 완료 scheduleId={}, notificationCount={}", schedule.getId(), notifications.size());
        }
    }

    @Transactional
    public void createReviewCreatedNotifications(Schedule schedule, ScheduleParticipant writerParticipant) {
        Member writer = writerParticipant.getMember();

        List<ScheduleParticipant> participants = scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED);

        List<Notification> notifications = participants.stream()
                .filter(participant -> participant.getMember() != null)
                .filter(participant -> !participant.getMember().getId().equals(writer.getId()))
                .filter(participant -> participant.getMember().getDeletedAt() == null)
                .filter(participant -> !notificationRepository
                        .existsByMemberAndNotificationTypeAndReferenceId(participant.getMember(), NotificationType.RECORD_CREATED, schedule.getId()))
                .map(participant -> NotificationConverter.toRecordCreated(participant.getMember(), schedule, writer))
                .toList();

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
        }

        log.info("기록 작성 알림 생성 완료 scheduleId={}, writerId={}, notificationCount={}", schedule.getId(), writer.getId(), notifications.size());
    }

    @Transactional
    public void createWeeklyMissionCompletedNotifications(FamilyMissionRecommendation recommendation, Member writer) {
        Family family = recommendation.getFamily();

        List<Member> members = memberRepository.findAllByFamilyAndDeletedAtIsNull(family);

        List<Notification> notifications = members.stream()
                .filter(member -> !member.getId().equals(writer.getId()))
                .filter(member -> !notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member, NotificationType.WEEKLY_MISSION_COMPLETED, recommendation.getId()))
                .map(member -> NotificationConverter.toWeeklyMissionCompleted(member, recommendation))
                .toList();

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
        }

        log.info("주간 미션 완료 알림 생성 완료 recommendationId={}, writerId={}, notificationCount={}", recommendation.getId(), writer.getId(), notifications.size());
    }

    @Transactional
    public void createExternalEventCancelledNotifications(Mission mission) {
        List<Schedule> schedules = scheduleRepository.findAllActiveByMissionId(mission.getId(), List.of(ScheduleStatus.PENDING_RESPONSE, ScheduleStatus.CONFIRMED));

        if (schedules.isEmpty()) {
            return;
        }

        Map<Long, Member> recipients = new LinkedHashMap<>();

        for (Schedule schedule : schedules) {
            Member proposer = schedule.getProposer();
            if (proposer != null) {
                recipients.put(proposer.getId(), proposer);
            }

            List<ScheduleParticipant> participants = scheduleParticipantRepository.findAllConfirmedWithMemberByScheduleIdAndStatus(schedule.getId(), ParticipantStatus.ACCEPTED);

            for (ScheduleParticipant participant : participants) {
                Member member = participant.getMember();

                if (member != null) {
                    recipients.put(member.getId(), member);
                }
            }
        }

        List<Notification> notifications = new ArrayList<>();

        for (Member member : recipients.values()) {
            boolean alreadyExists = notificationRepository.existsByMemberAndNotificationTypeAndReferenceId(member, NotificationType.EXTERNAL_EVENT_CANCELLED, mission.getId());

            if (alreadyExists) {
                continue;
            }

            notifications.add(NotificationConverter.toExternalEventCancelled(member, mission));
        }

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
        }
    }

    @Transactional
    public List<NotificationResDTO.NotificationProcessingTarget> claimPendingNotifications() {
        LocalDateTime now = LocalDateTime.now();

        List<Notification> notifications = notificationRepository.findPendingNotificationsForProcessing(now, NOTIFICATION_BATCH_SIZE);

        List<NotificationResDTO.NotificationProcessingTarget> targets = new ArrayList<>();

        for (Notification notification : notifications) {
            UUID leaseId = notification.startProcessing();

            if (leaseId == null) {
                continue;
            }

            targets.add(new NotificationResDTO.NotificationProcessingTarget(notification.getId(), notification.getMember().getId(), notification.getFamilyId(), notification.getTitle(), notification.getBody(), notification.getNotificationType(), notification.getReferenceDate(), leaseId));
        }

        log.info("알림 발송 대상 선점 완료 notificationCount={}", targets.size());

        return targets;
    }

    @Transactional
    public int recoverStaleProcessingNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(PROCESSING_TIMEOUT_MINUTES);

        List<Notification> notifications = notificationRepository.findStaleProcessingNotifications(threshold, NOTIFICATION_BATCH_SIZE);

        for (Notification notification : notifications) {
            notification.recoverProcessing();
        }

        log.info("중단된 알림 복구 완료 notificationCount={}", notifications.size());

        return notifications.size();
    }

    @Transactional
    public void markNotificationSent(Long notificationId, UUID leaseId) {
        notificationRepository.findById(notificationId).ifPresent(notification -> notification.send(leaseId));
    }

    @Transactional
    public void markNotificationFailed(Long notificationId, UUID leaseId) {
        notificationRepository.findById(notificationId).ifPresent(notification -> notification.fail(leaseId));
    }

    @Transactional
    public void markNotificationSkipped(Long notificationId, UUID leaseId) {
        notificationRepository.findById(notificationId).ifPresent(notification -> notification.skip(leaseId));
    }

    @Transactional
    public int retryFailedNotifications() {
        LocalDateTime now = LocalDateTime.now();

        List<Notification> notifications = notificationRepository.findRetryableNotifications(now, NOTIFICATION_BATCH_SIZE);

        for (Notification notification : notifications) {
            notification.retry();
        }

        log.info("실패한 알림 재시도 대상 등록 완료 notificationCount={}", notifications.size());

        return notifications.size();
    }

    @Transactional
    public void deferNotification(Long notificationId, UUID leaseId, LocalDateTime scheduledAt) {
        notificationRepository.findById(notificationId).ifPresent(notification -> notification.defer(leaseId, scheduledAt));
    }

    @Transactional
    public void expireNotification(Long notificationId, UUID leaseId) {
        notificationRepository.findById(notificationId).ifPresent(notification -> notification.expire(leaseId));
    }

    @Transactional(readOnly = true)
    public boolean hasReachedDailyPushLimit(Long memberId) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        long sentCount = notificationRepository.countByMember_IdAndPriorityAndStatusAndSentAtGreaterThanEqualAndSentAtLessThan(memberId, NotificationPriority.P5, NotificationStatus.SENT, start, end);

        return sentCount >= MAX_DAILY_PUSH_COUNT;
    }

    /**
     * 푸시를 보내면 안 되는 회원인지 확인한다. 인앱 알림은 그대로 두고 푸시만 생략한다.
     * 탈퇴했거나 가족공간에 속하지 않은 회원, 알림이 생긴 가족을 떠나 다른 가족으로 옮긴 회원,
     * 앱에서 전체 푸시를 끈 회원이 대상이다.
     *
     * @param notificationFamilyId 알림이 생긴 시점의 가족 ID. 가족 정보가 없는 옛 알림이면 null
     */
    @Transactional(readOnly = true)
    public boolean isPushBlocked(Long memberId, Long notificationFamilyId) {
        Member member = memberRepository.findById(memberId).orElse(null);

        if (member == null || member.isWithdrawn() || member.getFamily() == null) {
            return true;
        }

        if (notificationFamilyId != null && !notificationFamilyId.equals(member.getFamily().getId())) {
            return true;
        }

        return memberSettingRepository.findByMember(member)
                .map(setting -> !Boolean.TRUE.equals(setting.getIsAllNotificationEnabled()))
                .orElse(false);
    }

    private LocalDateTime calculateRecordReminderAt(LocalDateTime scheduleStartAt) {
        LocalDateTime reminderAt = scheduleStartAt.plusHours(6);
        LocalTime reminderTime = reminderAt.toLocalTime();

        if (!reminderTime.isBefore(LocalTime.of(22, 0))) {
            return reminderAt.toLocalDate().plusDays(1).atTime(9, 0);
        }

        if (reminderTime.isBefore(LocalTime.of(9, 0))) {
            return reminderAt.toLocalDate().atTime(9, 0);
        }

        return reminderAt;
    }
}
