package com.kiri.server.domain.member.exception;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;

public class MemberException extends ProjectException {

    public MemberException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
