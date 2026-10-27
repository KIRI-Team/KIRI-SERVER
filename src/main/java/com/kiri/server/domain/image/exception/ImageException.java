package com.kiri.server.domain.image.exception;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;

public class ImageException extends ProjectException {
    public ImageException(BaseErrorCode errorCode) {
        super(errorCode);
    }

    public ImageException(BaseErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
