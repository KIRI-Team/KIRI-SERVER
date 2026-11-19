package com.kiri.server.domain.term.exception;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;

public class TermException extends ProjectException {

    public TermException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
