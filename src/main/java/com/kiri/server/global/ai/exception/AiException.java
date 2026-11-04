package com.kiri.server.global.ai.exception;


import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;

public class AiException extends ProjectException {
    public AiException(BaseErrorCode errorCode) {
        super(errorCode);
    }

    public AiException(BaseErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
