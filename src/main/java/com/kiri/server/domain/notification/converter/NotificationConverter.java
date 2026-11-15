package com.kiri.server.domain.notification.converter;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.notification.entity.Notification;
import com.kiri.server.domain.notification.enums.NotificationStatus;
import com.kiri.server.domain.notification.enums.NotificationType;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import com.kiri.server.domain.schedule.entity.Schedule;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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

    public static Notification toWeeklyMission(Member member, Mission mission, LocalDate referenceDate) {
        String missionTitle = mission.getTitle();

        return Notification.builder()
                .member(member)
                .notificationType(NotificationType.MISSION)
                .title("이번 주 새로운 가족 미션이 도착했어요!")
                .body("이번 주 우리 가족의 미션은 '" + missionTitle + "'이에요. 함께할 날짜를 정해 보세요.")
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(mission.getId())
                .referenceDate(referenceDate)
                .build();
    }

    public static Notification toMissionChanged(Member member, Mission mission, LocalDate referenceDate) {
        String missionTitle = mission.getTitle();

        return Notification.builder()
                .member(member)
                .notificationType(NotificationType.MISSION_CHANGED)
                .title("이번 주 가족 미션이 변경됐어요!")
                .body("기존 미션을 진행할 수 없어 새로운 미션 '" + missionTitle + "'으로 변경했어요. 함께할 날짜를 정해 보세요.")
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(mission.getId())
                .referenceDate(referenceDate)
                .build();
    }

    public static Notification toScheduleInvitation(Member receiver, Schedule schedule) {
        String proposerNickname = schedule.getProposer().getNickname();
        String missionTitle = schedule.getBucketItem().getMission().getTitle();

        String scheduleDate = schedule.getScheduleDate().format(DateTimeFormatter.ofPattern("M월 d일"));

        String scheduleTime = schedule.getScheduleTime().format(DateTimeFormatter.ofPattern("HH:mm"));

        return Notification.builder()
                .member(receiver)
                .notificationType(NotificationType.SCHEDULE_INVITATION)
                .title("초대장이 도착했어요! 💌")
                .body(proposerNickname + "님이 '" + missionTitle + "'에 초대했어요. " + scheduleDate + " " + scheduleTime + "에 함께할지 알려주세요.")
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(schedule.getId())
                .referenceDate(schedule.getScheduleDate())
                .build();
    }

    public static Notification toScheduleInvitationReminder(Member receiver, Schedule schedule) {
        String proposerNickname = schedule.getProposer().getNickname();
        String missionTitle = schedule.getBucketItem().getMission().getTitle();
        String scheduleDate = schedule.getScheduleDate()
                .format(DateTimeFormatter.ofPattern("M월 d일"));
        String scheduleTime = schedule.getScheduleTime()
                .format(DateTimeFormatter.ofPattern("HH:mm"));

        return Notification.builder()
                .member(receiver)
                .notificationType(NotificationType.SCHEDULE_INVITATION_REMINDER)
                .title("일정 초대에 아직 응답하지 않았어요!")
                .body(proposerNickname + "님이 초대한 '" + missionTitle + "' 일정(" + scheduleDate + " " + scheduleTime + ")에 아직 응답하지 않았어요. 참여 여부를 알려주세요.")
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(schedule.getId())
                .referenceDate(schedule.getScheduleDate())
                .referenceVersion(schedule.getInviteVersion())
                .build();
    }

    public static Notification toScheduleConfirmed(Member receiver, Schedule schedule) {
        String missionTitle = schedule.getBucketItem().getMission().getTitle();
        String scheduleDate = schedule.getScheduleDate()
                .format(DateTimeFormatter.ofPattern("M월 d일"));
        String scheduleTime = schedule.getScheduleTime()
                .format(DateTimeFormatter.ofPattern("HH:mm"));

        return Notification.builder()
                .member(receiver)
                .notificationType(NotificationType.SCHEDULE)
                .title("일정이 확정됐어요! 🎉")
                .body("'" + missionTitle + "' 일정이 확정됐어요. "
                        + scheduleDate + " " + scheduleTime + "에 만나요!")
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(schedule.getId())
                .referenceDate(schedule.getScheduleDate())
                .build();
    }
}
