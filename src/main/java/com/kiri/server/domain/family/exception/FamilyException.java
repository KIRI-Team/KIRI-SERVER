package com.kiri.server.domain.family.exception;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;

public class FamilyException extends ProjectException {
    public FamilyException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
