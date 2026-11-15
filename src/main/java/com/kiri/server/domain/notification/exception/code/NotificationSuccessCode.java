package com.kiri.server.domain.notification.exception.code;

import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationSuccessCode implements BaseSuccessCode {

    GET_NOTIFICATION_LIST(HttpStatus.OK, "NOTIFICATION200_1", "알림 목록 조회에 성공했습니다."),
    READ_NOTIFICATION(HttpStatus.OK, "NOTIFICATION200_2", "알림 읽음 처리에 성공했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
