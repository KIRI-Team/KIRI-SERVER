package com.kiri.server.domain.mission.exception.code;

import com.kiri.server.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MissionSuccessCode implements BaseSuccessCode {

    FEED_SUCCESS(HttpStatus.OK, "MISSION200_1", "미션 피드 조회 성공"),
    DETAIL_SUCCESS(HttpStatus.OK, "MISSION200_2", "미션 상세 조회 성공"),
    RECOMMENDATION_SUCCESS(HttpStatus.OK, "MISSION200_3", "주간 미션 조회 성공");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
