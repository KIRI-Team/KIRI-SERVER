package com.kiri.server.domain.external.exception;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;
import lombok.Getter;

@Getter
public class ExternalException extends ProjectException {

    private final String detail;

    public ExternalException(BaseErrorCode errorCode) {
        this(errorCode, null);
    }

    public ExternalException(BaseErrorCode errorCode, String detail) {
        super(errorCode);
        this.detail = detail;
    }

    public ExternalException(BaseErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode, cause);
        this.detail = detail;
    }

    @Override
    public String getMessage() {
        String message = getErrorCode().getMessage();
        return detail == null ? message : message + " (" + detail + ")";
    }
}
