package com.kiri.server.admin.common;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AdminErrorCode implements BaseErrorCode {

    // 400
    REASON_REQUIRED(HttpStatus.BAD_REQUEST, "ADMIN400_1", "이 작업은 사유를 입력해야 합니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "ADMIN400_2", "요청 값이 올바르지 않습니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "ADMIN400_3", "검증을 통과하지 못했습니다."),
    UNSUPPORTED_SOURCE(HttpStatus.BAD_REQUEST, "ADMIN400_4", "수집기가 없는 출처입니다."),
    INVALID_STATUS(HttpStatus.BAD_REQUEST, "ADMIN400_5", "현재 상태에서는 할 수 없는 작업입니다."),
    PUBLISH_REQUIREMENT_MISSING(HttpStatus.BAD_REQUEST, "ADMIN400_6", "공개에 필요한 값이 비어 있습니다."),

    // 401
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "ADMIN401_1", "아이디 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "ADMIN401_2", "유효하지 않은 리프레시 토큰입니다."),
    PASSWORD_MISMATCH(HttpStatus.UNAUTHORIZED, "ADMIN401_3", "현재 비밀번호가 올바르지 않습니다."),

    // 404
    ADMIN_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN404_1", "존재하지 않는 관리자입니다."),
    TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN404_2", "대상을 찾을 수 없습니다."),

    // 409
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "ADMIN409_1", "이미 사용 중인 아이디입니다."),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "ADMIN409_2", "다른 작업이 먼저 반영되었습니다. 새로고침 후 다시 시도해 주세요."),
    SYNC_RUNNING(HttpStatus.CONFLICT, "ADMIN409_3", "이미 수집이 실행 중입니다."),
    QUESTION_ALREADY_ASSIGNED(HttpStatus.CONFLICT, "ADMIN409_4", "이미 가족에게 배정된 질문은 문구를 수정할 수 없습니다. 새 질문을 만들고 기존 질문을 중지해 주세요."),
    ALREADY_RESOLVED(HttpStatus.CONFLICT, "ADMIN409_5", "이미 처리된 검수 건입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
