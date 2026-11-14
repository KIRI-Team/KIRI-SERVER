package com.kiri.server.domain.notification.service;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.notification.converter.NotificationConverter;
import com.kiri.server.domain.notification.entity.Notification;
import com.kiri.server.domain.notification.enums.NotificationStatus;
import com.kiri.server.domain.notification.enums.NotificationType;
import com.kiri.server.domain.notification.repository.NotificationRepository;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import com.kiri.server.domain.question.repository.FamilyQuestionRepository;
import com.kiri.server.domain.question.repository.MemberAnswerRepository;
import com.kiri.server.domain.setting.entity.MemberSetting;
import com.kiri.server.domain.setting.repository.MemberSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final FamilyQuestionRepository familyQuestionRepository;
    private final MemberRepository memberRepository;
    private final MemberSettingRepository memberSettingRepository;
    private final MemberAnswerRepository memberAnswerRepository;
    private final NotificationRepository notificationRepository;

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

        if (notificationRepository.existsByMemberAndNotificationTypeAndReferenceDate(member, NotificationType.QUESTION, familyQuestion.getQuestionDate())) {
            return;
        }

        Notification notification = NotificationConverter.toQuestionReminder(member, familyQuestion);

        notificationRepository.save(notification);
    }

    @Transactional
    public void cancelQuestionReminder(Member member, FamilyQuestion familyQuestion) {
        List<Notification> notifications = notificationRepository.findAllByMemberAndNotificationTypeAndReferenceIdAndStatus(member, NotificationType.QUESTION, familyQuestion.getId(), NotificationStatus.PENDING);

        notifications.forEach(Notification::cancel);
    }
}
