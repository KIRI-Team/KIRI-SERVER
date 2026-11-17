package com.kiri.server.domain.notification.enums;

/**
 * 알림을 눌렀을 때 앱이 이동할 화면. referenceId·referenceDate가 무엇을 가리키는지도 함께 정한다.
 */
public enum NotificationTargetType {

    /** 해당 날짜의 가족 질문. referenceId = familyQuestionId, referenceDate = 질문 날짜 */
    QUESTION,

    /** 해당 날짜의 가족 답변 모아보기. referenceId = familyQuestionId, referenceDate = 질문 날짜 */
    FAMILY_ANSWERS,

    /** 홈의 주간 미션 카드. referenceId = missionId, referenceDate = 주간 미션 시작일 */
    WEEKLY_MISSION,

    /** 일정 초대 상세. referenceId = scheduleId */
    SCHEDULE_INVITATION,

    /** 일정 상세. referenceId = scheduleId */
    SCHEDULE,

    /** 해당 활동의 리뷰 작성. referenceId = scheduleId */
    REVIEW_WRITE,

    /** 완료 활동 기록 상세. referenceId = scheduleId */
    SCHEDULE_RECORD,

    /** 주간 미션 완료 기록. referenceId = familyMissionRecommendationId */
    WEEKLY_MISSION_RECORD,

    /** 미션 상세. referenceId = missionId */
    MISSION
}
