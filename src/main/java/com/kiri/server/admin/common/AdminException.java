package com.kiri.server.admin.common;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;

public class AdminException extends ProjectException {

    public AdminException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
