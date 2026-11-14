package com.kiri.server.domain.notification.converter;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.notification.entity.Notification;
import com.kiri.server.domain.notification.enums.NotificationStatus;
import com.kiri.server.domain.notification.enums.NotificationType;
import com.kiri.server.domain.question.entity.FamilyQuestion;

public class NotificationConverter {

    public static Notification toQuestionReminder(
            Member member,
            FamilyQuestion familyQuestion
    ) {
        return Notification.builder()
                .member(member)
                .notificationType(NotificationType.QUESTION)
                .title("아직 오늘의 질문에 답변하지 않았어요!")
                .body("오늘 받은 질문카드는 오늘 내로 답변해 주세요. 가족들이 기다리고 있어요.")
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(familyQuestion.getId())
                .referenceDate(familyQuestion.getQuestionDate())
                .build();
    }
}
