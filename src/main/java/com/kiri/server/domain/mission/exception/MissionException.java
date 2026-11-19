package com.kiri.server.domain.mission.exception;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;

public class MissionException extends ProjectException {

    public MissionException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
