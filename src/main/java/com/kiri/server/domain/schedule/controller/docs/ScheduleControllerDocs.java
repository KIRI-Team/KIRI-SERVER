package com.kiri.server.domain.schedule.controller.docs;

import com.kiri.server.domain.schedule.dto.ScheduleReqDTO;
import com.kiri.server.domain.schedule.dto.ScheduleResDTO;
import com.kiri.server.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;

@Tag(name = "일정 API")
public interface ScheduleControllerDocs {

    @Operation(
            summary = "캘린더 이번달 약속 조회 API",
            description = """
                    # 캘린더 월간 일정 조회

                    ## 요청 형식
                    - 헤더: Authorization: Bearer {JWT 토큰}
                    - Query: year, month (둘 다 필수)

                    기록 탭 캘린더에서 현재 로그인 회원이 속한 가족공간의 해당 연·월 일정을 날짜·시간순으로 조회합니다.

                    ## 응답 규칙
                    - status: 일정 전체 상태 (PENDING_RESPONSE 응답대기 / CONFIRMED 확정 / NOT_CONFIRMED 미확정 / CANCELLED 취소 / COMPLETED 완료)
                    - myStatus: 현재 회원의 응답 상태 (NOT_INVITED 초대 안 됨 / PENDING 미응답 / ACCEPTED 수락 / DECLINED 거절 / RECONFIRM_REQUIRED 재확인 필요)
                    - 초대받지 않은 일정도 같은 가족이면 조회되며 myStatus는 NOT_INVITED 입니다.
                    - 취소 일정도 숨기지 않고 status=CANCELLED로 내려줍니다. (프론트에서 흐리게 처리)
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "SCHEDULE200_1",
                                      "message": "캘린더 월간 일정 조회 성공",
                                      "result": {
                                        "year": 2026,
                                        "month": 10,
                                        "schedules": [
                                          {
                                            "scheduleId": 100,
                                            "date": "2026-10-03",
                                            "time": "14:50",
                                            "status": "CONFIRMED",
                                            "myStatus": "ACCEPTED"
                                          },
                                          {
                                            "scheduleId": 101,
                                            "date": "2026-10-24",
                                            "time": "10:00",
                                            "status": "PENDING_RESPONSE",
                                            "myStatus": "NOT_INVITED"
                                          }
                                        ]
                                      }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "year·month 누락 또는 범위를 벗어난 값",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "COMMON400_1",
                                      "message": "잘못된 요청입니다.",
                                      "result": {
                                        "month": "month는 1 이상 12 이하여야 합니다."
                                      }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "COMMON401_1",
                                      "message": "인증이 필요합니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "가족공간이 없는 회원",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "SCHEDULE403_1",
                                      "message": "가족 공간이 없습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<ScheduleResDTO.Calendar> getCalendar(
            @Parameter(hidden = true) Long memberId,
            @Valid @ParameterObject ScheduleReqDTO.CalendarCondition condition
    );

    @Operation(
            summary = "다가오는 일정 조회 API",
            description = """
                    # 홈 상단 다가오는 일정 조회

                    ## 요청 형식
                    - 헤더: Authorization: Bearer {JWT 토큰}
                    - 파라미터 없음 (현재 로그인 회원·가족 기준)

                    홈 상단에 노출할, 현재 회원 관련 일정을 상태에 맞게 내려줍니다.

                    ## 응답 규칙
                    - responseRequired: 내가 아직 응답하지 않은(PENDING/재확인 필요) 일정 1건. 응답 가능(시작 전·마감 전)한 것 중 가장 가까운 1건이며, 없으면 null.
                    - upcoming: 내가 최종 참여자인 CONFIRMED 일정 중 시작일 D-7~D-Day의 가장 가까운 1건. 없으면 null.
                    - 두 카드는 별개이며, 프론트가 우선순위(responseRequired 우선)를 적용해 노출합니다.
                    - type: RESPONSE_REQUIRED(응답 필요) / UPCOMING(다가오는, D-1~D-7) / TODAY(오늘, D-Day)
                    - dDay: 오늘 기준 시작일까지 남은 일수(0=당일)
                    - myStatus: 현재 회원의 응답 상태 (PENDING / ACCEPTED / RECONFIRM_REQUIRED 등)
                    - CANCELLED / NOT_CONFIRMED / COMPLETED 일정은 제외합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "SCHEDULE200_5",
                                      "message": "다가오는 일정 조회 성공",
                                      "result": {
                                        "responseRequired": {
                                          "scheduleId": 120,
                                          "date": "2026-11-20",
                                          "time": "18:00",
                                          "status": "PENDING_RESPONSE",
                                          "myStatus": "PENDING",
                                          "dDay": 5,
                                          "type": "RESPONSE_REQUIRED",
                                          "title": "광장시장에서 시장음식 먹어보기"
                                        },
                                        "upcoming": {
                                          "scheduleId": 118,
                                          "date": "2026-11-17",
                                          "time": "14:50",
                                          "status": "CONFIRMED",
                                          "myStatus": "ACCEPTED",
                                          "dDay": 2,
                                          "type": "UPCOMING",
                                          "title": "한강 뚜벅뚜벅 축제 가보기"
                                        }
                                      }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "COMMON401_1",
                                      "message": "인증이 필요합니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "가족공간이 없는 회원",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "SCHEDULE403_1",
                                      "message": "가족 공간이 없습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<ScheduleResDTO.UpcomingHome> getUpcoming(
            @Parameter(hidden = true) Long memberId
    );

    @Operation(
            summary = "컨디션 커스텀 조회 API",
            description = """
                    # 컨디션 커스텀 화면 조회

                    ## 요청 형식
                    - 헤더: Authorization: Bearer {JWT 토큰}
                    - Path: scheduleId

                    컨디션 조정 화면에 필요한 일정의 미션 정보와 현재 적용된 컨디션 조건을 조회합니다.

                    ## 응답 규칙
                    - missionTitle, estimatedSteps*, duration*: 일정에 연결된 미션의 값 (범위는 min~max로 표시)
                    - conditions: 현재 일정에 적용된 조건 목록. 적용한 조건이 없으면 빈 배열입니다.
                      (REDUCE_STEPS 걸음 수 낮추기 / MORE_REST 휴식 시간 늘리기 / LOWER_BUDGET 예산 낮추기 / AVOID_CROWD 사람 많은 곳 피하기)
                    - 같은 가족의 일정만 조회할 수 있으며, 다른 가족의 일정은 존재하지 않는 일정과 같이 404로 응답합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "적용한 조건 있음",
                                            value = """
                                                    {
                                                      "isSuccess": true,
                                                      "code": "SCHEDULE200_6",
                                                      "message": "컨디션 커스텀 조회 성공",
                                                      "result": {
                                                        "missionTitle": "한강 뚜벅뚜벅 축제 가보기",
                                                        "estimatedStepsMin": 5000,
                                                        "estimatedStepsMax": 12000,
                                                        "durationMinMinutes": 60,
                                                        "durationMaxMinutes": 180,
                                                        "conditions": ["REDUCE_STEPS", "MORE_REST"]
                                                      }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "적용한 조건 없음",
                                            value = """
                                                    {
                                                      "isSuccess": true,
                                                      "code": "SCHEDULE200_6",
                                                      "message": "컨디션 커스텀 조회 성공",
                                                      "result": {
                                                        "missionTitle": "한강 뚜벅뚜벅 축제 가보기",
                                                        "estimatedStepsMin": 5000,
                                                        "estimatedStepsMax": 12000,
                                                        "durationMinMinutes": 60,
                                                        "durationMaxMinutes": 180,
                                                        "conditions": []
                                                      }
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "scheduleId 형식 오류(COMMON400_1)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "COMMON400_1",
                                      "message": "잘못된 요청입니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 - 토큰 미삽입(COMMON401_1) / 유효하지 않은 토큰(AUTH401_1) / 토큰 만료(AUTH401_2)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "토큰 미삽입",
                                            value = """
                                                    {
                                                      "isSuccess": false,
                                                      "code": "COMMON401_1",
                                                      "message": "인증이 필요합니다.",
                                                      "result": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "유효하지 않은 토큰",
                                            value = """
                                                    {
                                                      "isSuccess": false,
                                                      "code": "AUTH401_1",
                                                      "message": "유효하지 않은 토큰입니다.",
                                                      "result": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "토큰 만료",
                                            value = """
                                                    {
                                                      "isSuccess": false,
                                                      "code": "AUTH401_2",
                                                      "message": "토큰이 만료되었습니다",
                                                      "result": null
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "가족공간이 없는 회원(SCHEDULE403_1)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "SCHEDULE403_1",
                                      "message": "가족 공간이 없습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "토큰의 회원이 없음(MEMBER404_1) / 없거나 다른 가족의 일정(SCHEDULE404_2)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "회원 없음",
                                            value = """
                                                    {
                                                      "isSuccess": false,
                                                      "code": "MEMBER404_1",
                                                      "message": "존재하지 않는 회원입니다.",
                                                      "result": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "일정 없음",
                                            value = """
                                                    {
                                                      "isSuccess": false,
                                                      "code": "SCHEDULE404_2",
                                                      "message": "존재하지 않는 일정입니다.",
                                                      "result": null
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    })
    ApiResponse<ScheduleResDTO.Conditions> getConditions(
            @Parameter(hidden = true) Long memberId,
            @Parameter(description = "일정 ID", example = "118") Long scheduleId
    );
}
