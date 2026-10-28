package com.kiri.server.domain.mission.exception.code;

import com.kiri.server.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MissionErrorCode implements BaseErrorCode {

    INVALID_DISTANCE(HttpStatus.BAD_REQUEST, "MISSION400_1", "거리는 1, 3, 5, 10km 중 하나여야 합니다."),
    COORDINATE_PAIR_REQUIRED(HttpStatus.BAD_REQUEST, "MISSION400_2", "위도와 경도는 함께 입력해야 합니다."),
    INVALID_REGION_CODE(HttpStatus.BAD_REQUEST, "MISSION400_3", "지역 코드는 2자리 또는 5자리여야 합니다."),

    NOT_FOUND(HttpStatus.NOT_FOUND, "MISSION404_1", "존재하지 않는 미션입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
