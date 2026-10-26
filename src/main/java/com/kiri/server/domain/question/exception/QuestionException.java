package com.kiri.server.domain.question.exception;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;

public class QuestionException extends ProjectException {
    public QuestionException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
