package com.kiri.server.domain.schedule.exception;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;

public class ScheduleException extends ProjectException {

    public ScheduleException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
