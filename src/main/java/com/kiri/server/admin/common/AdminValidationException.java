package com.kiri.server.admin.common;

import com.kiri.server.admin.content.dto.MissionFormDTO;
import lombok.Getter;

import java.util.List;

// 필드별 검증 오류 목록을 응답 result에 담아 돌려준다.
@Getter
public class AdminValidationException extends AdminException {

    private final List<MissionFormDTO.FieldError> errors;

    public AdminValidationException(List<MissionFormDTO.FieldError> errors) {
        super(AdminErrorCode.VALIDATION_FAILED);
        this.errors = List.copyOf(errors);
    }
}
