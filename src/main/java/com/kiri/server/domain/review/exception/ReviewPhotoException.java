package com.kiri.server.domain.review.exception;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;

public class ReviewPhotoException extends ProjectException {
  public ReviewPhotoException(BaseErrorCode errorCode) {
    super(errorCode);
  }
  public ReviewPhotoException(BaseErrorCode errorCode, Throwable cause) {
    super(errorCode, cause);
  }
}
