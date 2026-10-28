package com.kiri.server.domain.taste.exception;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;

public class TasteException extends ProjectException {
    public TasteException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
