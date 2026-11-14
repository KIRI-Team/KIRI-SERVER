package com.kiri.server.admin.content.service;

import com.kiri.server.admin.content.dto.MissionFormDTO;
import com.kiri.server.domain.mission.enums.ItemType;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.mission.generator.ChecklistPlanner;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// 운영자 입력값 검증. 공개 가능한 미션의 필수 조건을 확인한다.
public final class MissionFormValidator {

    static final int MAX_TITLE_LENGTH = 100;
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("33");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("39");
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("124");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("132");
    private static final Pattern URL = Pattern.compile("^https?://\\S+$");

    private MissionFormValidator() {
    }

    // 공개 기준 전체 검증 (검수 승인, 초안 공개)
    public static MissionFormDTO.ValidationResult validateForPublish(MissionFormDTO.MissionForm form) {
        List<MissionFormDTO.FieldError> errors = new ArrayList<>(validateBasic(form));
        if (isBlank(form.description())) {
            errors.add(error("description", "소개는 필수입니다."));
        }
        long steps = form.checklist().stream().filter(item -> item.itemType() == ItemType.STEP).count();
        if (steps < ChecklistPlanner.MIN_STEPS) {
            errors.add(error("checklist", "필수 활동(STEP)은 " + ChecklistPlanner.MIN_STEPS + "개 이상이어야 합니다."));
        }
        return new MissionFormDTO.ValidationResult(errors.isEmpty(), errors);
    }

    // 초안 저장 기준 검증. 저장에 꼭 필요한 값과 값 형식만 본다.
    public static MissionFormDTO.ValidationResult validateForDraft(MissionFormDTO.MissionForm form) {
        List<MissionFormDTO.FieldError> errors = validateBasic(form);
        return new MissionFormDTO.ValidationResult(errors.isEmpty(), errors);
    }

    private static List<MissionFormDTO.FieldError> validateBasic(MissionFormDTO.MissionForm form) {
        List<MissionFormDTO.FieldError> errors = new ArrayList<>();
        if (isBlank(form.title())) {
            errors.add(error("title", "미션명은 필수입니다."));
        } else if (form.title().strip().length() > MAX_TITLE_LENGTH) {
            errors.add(error("title", "미션명은 100자 이하여야 합니다."));
        }
        if (form.category() == null) {
            errors.add(error("category", "카테고리는 필수입니다."));
        }
        if (form.missionType() == null) {
            errors.add(error("missionType", "상시·기간형 구분은 필수입니다."));
        } else if (form.missionType() == MissionType.PERIOD) {
            if (form.startDate() == null || form.endDate() == null) {
                errors.add(error("period", "기간형 미션은 시작일과 종료일이 필요합니다."));
            } else if (form.endDate().isBefore(form.startDate())) {
                errors.add(error("period", "종료일은 시작일보다 빠를 수 없습니다."));
            }
        }
        if (form.price() != null && form.price() < 0) {
            errors.add(error("price", "가격은 0 이상이어야 합니다."));
        }
        if (form.durationMinMinutes() != null && form.durationMaxMinutes() != null
                && form.durationMinMinutes() > form.durationMaxMinutes()) {
            errors.add(error("duration", "최소 소요시간이 최대 소요시간보다 클 수 없습니다."));
        }
        if (!isBlank(form.reservationUrl()) && !URL.matcher(form.reservationUrl().strip()).matches()) {
            errors.add(error("reservationUrl", "예약 링크는 http(s) 주소여야 합니다."));
        }
        if (form.checklist().stream().anyMatch(item -> item.itemType() == null || isBlank(item.title()))) {
            errors.add(error("checklist", "체크리스트 항목은 종류와 제목이 필요합니다."));
        }
        validatePlace(form.place(), errors);
        return errors;
    }

    private static void validatePlace(MissionFormDTO.PlaceForm place, List<MissionFormDTO.FieldError> errors) {
        if (place == null) {
            errors.add(error("place", "장소는 필수입니다."));
            return;
        }
        if (isBlank(place.name())) {
            errors.add(error("place.name", "장소명은 필수입니다."));
        } else if (place.name().strip().length() > MAX_TITLE_LENGTH) {
            errors.add(error("place.name", "장소명은 100자 이하여야 합니다."));
        }
        if (place.placeType() == null) {
            errors.add(error("place.placeType", "장소 유형은 필수입니다."));
        }
        if (place.latitude() == null || place.longitude() == null) {
            errors.add(error("place.coordinate", "좌표는 필수입니다."));
        } else if (place.latitude().compareTo(MIN_LATITUDE) < 0 || place.latitude().compareTo(MAX_LATITUDE) > 0
                || place.longitude().compareTo(MIN_LONGITUDE) < 0 || place.longitude().compareTo(MAX_LONGITUDE) > 0) {
            errors.add(error("place.coordinate", "좌표가 국내 범위를 벗어났습니다."));
        }
    }

    private static MissionFormDTO.FieldError error(String field, String message) {
        return new MissionFormDTO.FieldError(field, message);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
