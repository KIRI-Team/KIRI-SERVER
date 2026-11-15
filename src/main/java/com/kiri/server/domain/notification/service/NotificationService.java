package com.kiri.server.domain.notification.service;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.notification.converter.NotificationConverter;
import com.kiri.server.domain.notification.entity.Notification;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

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

        if (!Boolean.TRUE.equals(setting.getIsAllNotificationEnabled())) {
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

        List<NotificationType> scheduleNotificationTypes = List.of(NotificationType.SCHEDULE, NotificationType.SCHEDULE_INVITATION, NotificationType.SCHEDULE_INVITATION_REMINDER, NotificationType.SCHEDULE_RESPONSE, NotificationType.SCHEDULE_CHANGED, NotificationType.SCHEDULE_REMOVED, NotificationType.SCHEDULE_RECONFIRM_REMINDER, NotificationType.SCHEDULE_D1_REMINDER, NotificationType.SCHEDULE_D0_REMINDER);

        List<Notification> pendingNotifications = notificationRepository.findAllByNotificationTypeInAndReferenceIdAndStatus(scheduleNotificationTypes, scheduleId, NotificationStatus.PENDING);

        pendingNotifications.forEach(Notification::cancel);

        List<ScheduleParticipant> participants = scheduleParticipantRepository.findAllWithMemberByScheduleId(scheduleId);

        Map<Long, Member> receivers = new LinkedHashMap<>();

        participants.stream()
                .filter(participant -> participant.getMember() != null)
                .map(ScheduleParticipant::getMember)
                .filter(member -> !member.getId().equals(proposerId))
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
