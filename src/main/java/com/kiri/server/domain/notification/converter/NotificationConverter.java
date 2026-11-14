package com.kiri.server.domain.notification.converter;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.notification.entity.Notification;
import com.kiri.server.domain.notification.enums.NotificationStatus;
import com.kiri.server.domain.notification.enums.NotificationType;
import com.kiri.server.domain.question.entity.FamilyQuestion;

public class NotificationConverter {

    public static Notification toQuestionReminder(Member member, FamilyQuestion familyQuestion) {
        return Notification.builder()
                .member(member)
                .notificationType(NotificationType.QUESTION_REMINDER)
                .title("아직 오늘의 질문에 답변하지 않았어요!")
                .body("오늘 받은 질문카드는 오늘 내로 답변해 주세요. 가족들이 기다리고 있어요.")
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(familyQuestion.getId())
                .referenceDate(familyQuestion.getQuestionDate())
                .build();
    }

    public static Notification toFamilyAnswerComplete(Member member, FamilyQuestion familyQuestion) {
        return Notification.builder()
                .member(member)
                .notificationType(NotificationType.QUESTION_COMPLETE)
                .title("오늘은 가족 모두가 답변했어요!")
                .body("우리 가족의 생각이 모두 모였어요. 어떤 답변을 남겼는지 확인해 보세요.")
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(familyQuestion.getId())
                .referenceDate(familyQuestion.getQuestionDate())
                .build();
    }

    public static Notification toPoke(Member receiver, Member sender, FamilyQuestion familyQuestion, boolean hasAnswered) {
        String senderNickname = sender.getNickname();

        return Notification.builder()
                .member(receiver)
                .notificationType(NotificationType.POKE)
                .title("'" + senderNickname + "'님이 나를 '콕' 찔렀어요!")
                .body(hasAnswered ? senderNickname + "님이 관심을 보냈어요. 가족의 답변도 확인해 보세요." : senderNickname + "님이 오늘의 질문 답변을 기다리고 있어요. 들어가서 확인해 주세요.")
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(familyQuestion.getId())
                .referenceDate(familyQuestion.getQuestionDate())
                .build();
    }
}
