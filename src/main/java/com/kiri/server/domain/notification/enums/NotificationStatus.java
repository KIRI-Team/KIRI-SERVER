package com.kiri.server.domain.notification.enums;

public enum NotificationStatus {
    PENDING,    // 발송 대기
    SENT,   // FCM 발송 요청 성공
    CANCELLED,  // 발송 취소
    FAILED, // 발송 실패
    PROCESSING, // 발송 처리 중
    EXPIRED,    // 발송 유효 시간 만료
    SKIPPED // 푸시 발송 대상에서 제외
}
