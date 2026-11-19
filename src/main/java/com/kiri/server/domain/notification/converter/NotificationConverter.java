package com.kiri.server.domain.notification.converter;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.mission.entity.FamilyMissionRecommendation;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.notification.dto.NotificationResDTO;
import com.kiri.server.domain.notification.entity.Notification;
import com.kiri.server.domain.notification.enums.NotificationStatus;
import com.kiri.server.domain.notification.enums.NotificationType;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class NotificationConverter {

    public static Notification toQuestionReminder(Member member, FamilyQuestion familyQuestion) {
        return Notification.builder()
                .member(member)
                .notificationType(NotificationType.QUESTION_REMINDER)
                .title("아직 오늘의 질문에 답변하지 않았어요!")
                .body("오늘의 질문에 답하고 가족의 생각도 확인해 보세요.")
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
                .body("우리 가족의 이번 주 미션은 '" + missionTitle + "'이에요. 함께할 날짜를 정해 보세요.")
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
                .referenceVersion(schedule.getInviteVersion())
                .build();
    }

    public static Notification toScheduleInvitationReminder(Member receiver, Schedule schedule) {
        String missionTitle = schedule.getBucketItem().getMission().getTitle();

        return Notification.builder()
                .member(receiver)
                .notificationType(NotificationType.SCHEDULE_INVITATION_REMINDER)
                .title("아직 일정 초대에 답하지 않았어요!")
                .body("'" + missionTitle + "' 초대의 응답 마감이 다가오고 있어요. 함께할 수 있는지 알려주세요.")
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(schedule.getId())
                .referenceDate(schedule.getScheduleDate())
                .referenceVersion(schedule.getInviteVersion())
                .build();
    }

    public static Notification toScheduleConfirmed(Member receiver, Schedule schedule) {
        String missionTitle = schedule.getBucketItem().getMission().getTitle();
        String scheduleDate = schedule.getScheduleDate().format(DateTimeFormatter.ofPattern("M월 d일"));
        String scheduleTime = schedule.getScheduleTime().format(DateTimeFormatter.ofPattern("HH:mm"));

        return Notification.builder()
                .member(receiver)
                .notificationType(NotificationType.SCHEDULE)
                .title("일정이 확정됐어요! 🎉")
                .body("'" + missionTitle + "' 일정이 확정됐어요. " + scheduleDate + " " + scheduleTime + "에 만나요!")
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(schedule.getId())
                .referenceDate(schedule.getScheduleDate())
                .build();
    }

    public static Notification toScheduleResponse(Member receiver, Member responder, Schedule schedule, ParticipantStatus response) {
        String responderNickname = responder.getNickname();

        boolean accepted = response == ParticipantStatus.ACCEPTED;

        String title = accepted ? "일정 참여 응답이 도착했어요! 🎉" : "일정 참여 거절 응답이 도착했어요.";

        String body = accepted ? responderNickname + "님이 '" + schedule.getBucketItem().getMission().getTitle() + "' 일정 참여를 수락했어요." : responderNickname + "님이 '" + schedule.getBucketItem().getMission().getTitle() + "' 일정 참여를 거절했어요.";

        return Notification.builder()
                .member(receiver)
                .notificationType(NotificationType.SCHEDULE_RESPONSE)
                .title(title)
                .body(body)
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(schedule.getId())
                .referenceDate(schedule.getScheduleDate())
                .build();
    }

    public static Notification toScheduleChanged(Member receiver, Schedule schedule) {
        String missionTitle = schedule.getBucketItem().getMission().getTitle();
        String scheduleDate = schedule.getScheduleDate().format(DateTimeFormatter.ofPattern("M월 d일"));
        String scheduleTime = schedule.getScheduleTime().format(DateTimeFormatter.ofPattern("HH:mm"));

        return Notification.builder()
                .member(receiver)
                .notificationType(NotificationType.SCHEDULE_CHANGED)
                .title("일정이 변경됐어요!")
                .body("'" + missionTitle + "' 일정이 " + scheduleDate + " " + scheduleTime + "으로 변경됐어요. 변경된 일정을 확인해 주세요.")
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(schedule.getId())
                .referenceDate(schedule.getScheduleDate())
                .referenceVersion(schedule.getInviteVersion())
                .build();
    }

    public static Notification toScheduleRemoved(Member receiver, Schedule schedule) {
        String missionTitle = schedule.getBucketItem().getMission().getTitle();

        return Notification.builder()
                .member(receiver)
                .notificationType(NotificationType.SCHEDULE_REMOVED)
                .title("일정 참여 대상에서 제외됐어요.")
                .body("'" + missionTitle + "' 일정의 참여 대상이 변경되어 더 이상 일정에 참여하지 않아요.")
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(schedule.getId())
                .referenceDate(schedule.getScheduleDate())
                .referenceVersion(schedule.getInviteVersion())
                .build();
    }

    public static Notification toScheduleReconfirmReminder(Member receiver, Schedule schedule) {
        String missionTitle = schedule.getBucketItem().getMission().getTitle();

        return Notification.builder()
                .member(receiver)
                .notificationType(NotificationType.SCHEDULE_RECONFIRM_REMINDER)
                .title("변경된 일정을 다시 확인해 주세요!")
                .body("'" + missionTitle + "'의 일정이 바뀌었어요. 변경된 일정에 다시 수락 또는 거절해 주세요.")
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(schedule.getId())
                .referenceDate(schedule.getScheduleDate())
                .referenceVersion(schedule.getInviteVersion())
                .build();
    }

    public static Notification toScheduleCancelled(Member receiver, Schedule schedule) {
        String missionTitle = schedule.getBucketItem().getMission().getTitle();
        String proposerNickname = schedule.getProposer().getNickname();
        String scheduleDate = schedule.getScheduleDate().format(DateTimeFormatter.ofPattern("M월 d일"));

        return Notification.builder()
                .member(receiver)
                .notificationType(NotificationType.SCHEDULE_CANCELLED)
                .title("'" + missionTitle + "' 일정이 취소됐어요.")
                .body(proposerNickname + "님이 " + scheduleDate + " 일정을 취소했어요. 변경된 내용을 확인해 주세요.")
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(schedule.getId())
                .referenceDate(schedule.getScheduleDate())
                .build();
    }

    public static Notification toScheduleNotConfirmed(Member receiver, Schedule schedule) {
        String missionTitle = schedule.getBucketItem().getMission().getTitle();

        return Notification.builder()
                .member(receiver)
                .notificationType(NotificationType.SCHEDULE_NOT_CONFIRMED)
                .title("'" + missionTitle + "' 일정이 확정되지 않았어요.")
                .body("응답 마감까지 참여자가 확정되지 않았어요. 일정 내용을 확인해 주세요.")
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(schedule.getId())
                .referenceDate(schedule.getScheduleDate())
                .build();
    }

    public static Notification toScheduleD1Reminder(Member receiver, Schedule schedule) {
        String missionTitle = schedule.getBucketItem().getMission().getTitle();
        String scheduleTime = schedule.getScheduleTime().format(DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN));

        return Notification.builder()
                .member(receiver)
                .notificationType(NotificationType.SCHEDULE_D1_REMINDER)
                .title("내일은 가족과 함께하는 날이에요!")
                .body("내일 " + scheduleTime + "에 '" + missionTitle + "' 일정이 있어요. 미리 준비해 보세요.")
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(schedule.getId())
                .referenceVersion(schedule.getInviteVersion())
                .referenceDate(schedule.getScheduleDate())
                .build();
    }

    public static Notification toScheduleD0Reminder(Member receiver, Schedule schedule) {
        String missionTitle = schedule.getBucketItem().getMission().getTitle();

        return Notification.builder()
                .member(receiver)
                .notificationType(NotificationType.SCHEDULE_D0_REMINDER)
                .title("오늘 가족과 함께할 시간이 다가와요!")
                .body("'" + missionTitle + "' 일정이 2시간 후에 시작돼요. 출발 준비를 확인해 보세요.")
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(schedule.getId())
                .referenceVersion(schedule.getInviteVersion())
                .referenceDate(schedule.getScheduleDate())
                .build();
    }

    public static Notification toRecordReminder(Member member, Schedule schedule) {
        String missionTitle = schedule.getBucketItem().getMission().getTitle();

        return Notification.builder()
                .member(member)
                .notificationType(NotificationType.RECORD)
                .title("오늘 가족과 함께한 순간을 남겨보세요!")
                .body("'" + missionTitle + "' 활동은 어떠셨나요? 사진과 기록으로 오늘의 추억을 남겨보세요.")
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(schedule.getId())
                .referenceDate(schedule.getScheduleDate())
                .build();
    }

    public static Notification toRecordCreated(Member member, Schedule schedule, Member writer) {
        String writerNickname = writer.getNickname();
        String missionTitle = schedule.getBucketItem().getMission().getTitle();

        return Notification.builder()
                .member(member)
                .notificationType(NotificationType.RECORD_CREATED)
                .title("'" + writerNickname + "'님이 오늘 미션에 추억을 남겼어요!")
                .body("'" + missionTitle + "' 활동에 새로운 사진과 기록이 등록됐어요.")
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(schedule.getId())
                .referenceDate(schedule.getScheduleDate())
                .build();
    }

    public static Notification toWeeklyMissionCompleted(Member member, FamilyMissionRecommendation recommendation) {
        String missionTitle = recommendation.getMission().getTitle();

        return Notification.builder()
                .member(member)
                .notificationType(NotificationType.WEEKLY_MISSION_COMPLETED)
                .title("이번 주 가족 미션을 완료했어요!")
                .body("우리 가족이 '" + missionTitle + "' 미션을 완료했어요. 함께한 추억을 확인해 보세요.")
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(recommendation.getId())
                .referenceDate(recommendation.getStartedAt().toLocalDate())
                .build();
    }

    public static Notification toExternalEventCancelled(Member member, Mission mission) {
        String missionTitle = mission.getTitle();

        return Notification.builder()
                .member(member)
                .notificationType(NotificationType.EXTERNAL_EVENT_CANCELLED)
                .title("예정된 활동에 변경 사항이 있어요.")
                .body("'" + missionTitle + "' 행사 취소가 확인됐어요. 방문 전에 일정 내용을 확인해 주세요.")
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .referenceId(mission.getId())
                .build();
    }

    public static NotificationResDTO.NotificationPreview toNotificationPreview(Notification notification) {
        return NotificationResDTO.NotificationPreview.builder()
                .notificationId(notification.getId())
                .title(notification.getTitle())
                .body(notification.getBody())
                .notificationType(notification.getNotificationType())
                .category(notification.getNotificationType().getCategory())
                .targetType(notification.getNotificationType().getTargetType())
                .isRead(notification.getIsRead())
                .referenceId(notification.getReferenceId())
                .referenceDate(notification.getReferenceDate())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
