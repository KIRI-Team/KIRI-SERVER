package com.kiri.server.domain.bucket.exception;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;

public class BucketException extends ProjectException {

    public BucketException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
