package com.kiri.server.domain.review.exception;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;

public class ReviewException extends ProjectException {
  public ReviewException(BaseErrorCode errorCode) {
    super(errorCode);
  }
  public ReviewException(BaseErrorCode errorCode, Throwable cause) {
    super(errorCode, cause);
  }
}
