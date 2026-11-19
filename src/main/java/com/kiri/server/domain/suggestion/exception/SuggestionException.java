package com.kiri.server.domain.suggestion.exception;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import com.kiri.server.global.apiPayload.exception.ProjectException;

public class SuggestionException extends ProjectException {
    public SuggestionException(BaseErrorCode errorCode) {super(errorCode);}
    public SuggestionException(BaseErrorCode errorCode, Throwable cause) {
    super(errorCode, cause);
  }
}
