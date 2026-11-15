package com.kiri.server.domain.notification.exception;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;

public class NotificationException extends ProjectException {
    public NotificationException(BaseErrorCode errorCode) {
        super(errorCode);
    }
    public NotificationException(BaseErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
