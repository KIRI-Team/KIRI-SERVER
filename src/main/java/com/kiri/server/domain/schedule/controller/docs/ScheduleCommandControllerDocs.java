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

@Tag(name = "일정 API")
public interface ScheduleCommandControllerDocs {

    @Operation(
            summary = "일정 생성(초대 요청 보내기) API",
            description = """
                    # 일정 생성

                    ## 요청 형식
                    - 헤더: Authorization: Bearer {JWT 토큰}
                    - Path: bucketItemId (일정을 만들 버킷 항목)
                    - Body: 날짜·시간(필수), 초대할 가족 구성원 목록(제안자 제외, 1명 이상), 가족에게 한 줄(선택)

                    가족 버킷리스트의 '일정 잡기'에서 선택한 버킷 항목으로 일정을 만들고, 선택한 가족 구성원에게 초대를 보냅니다.

                    ## 규칙
                    - SAVED 상태이고 활성 일정이 연결되지 않은 버킷에서만 일정을 만들 수 있습니다.
                    - 제안자는 자동으로 수락(ACCEPTED) 처리되고, 선택한 구성원만 미응답(PENDING)으로 초대됩니다.
                    - 기간형 행사는 운영기간 안의 날짜만, 상시 장소형은 제한 없이 선택할 수 있습니다. 지난 시각은 선택할 수 없습니다.
                    - 응답 마감은 일정 시작 24시간 전이며, 생성 시점에 24시간 이하가 남았으면 시작 시각입니다.
                    - 일정이 만들어지면 버킷은 SCHEDULED 상태가 되고, 일정 전체 상태는 PENDING_RESPONSE로 시작합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "생성 성공 (응답 본문의 code는 SCHEDULE201_1)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "SCHEDULE201_1",
                                      "message": "일정 생성 성공",
                                      "result": {
                                        "scheduleId": 100,
                                        "bucketItemId": 42,
                                        "scheduleDate": "2026-11-15",
                                        "scheduleTime": "14:00",
                                        "status": "PENDING_RESPONSE",
                                        "responseDeadline": "2026-11-14T14:00:00",
                                        "proposerId": 1,
                                        "participantMemberIds": [2, 3]
                                      }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류(날짜·시간 누락/지난 시각/운영기간 밖/초대 대상 오류)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "필수값 누락", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "COMMON400_1",
                                              "message": "잘못된 요청입니다.",
                                              "result": { "scheduleDate": "scheduleDate는 필수입니다." }
                                            }
                                            """),
                                    @ExampleObject(name = "지난 시각", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "SCHEDULE400_2",
                                              "message": "지난 시각으로는 일정을 만들 수 없습니다.",
                                              "result": null
                                            }
                                            """),
                                    @ExampleObject(name = "운영기간 밖", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "SCHEDULE400_3",
                                              "message": "행사 운영기간 안의 날짜만 선택할 수 있습니다.",
                                              "result": null
                                            }
                                            """),
                                    @ExampleObject(name = "초대 대상 오류", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "SCHEDULE400_4",
                                              "message": "같은 가족 구성원만 초대할 수 있습니다.",
                                              "result": null
                                            }
                                            """)
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "가족공간 없음 또는 다른 가족의 버킷",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "가족공간 없음", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "SCHEDULE403_1",
                                              "message": "가족 공간이 없습니다.",
                                              "result": null
                                            }
                                            """),
                                    @ExampleObject(name = "다른 가족 버킷", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "SCHEDULE403_2",
                                              "message": "같은 가족의 버킷 항목에서만 일정을 만들 수 있습니다.",
                                              "result": null
                                            }
                                            """)
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 버킷 항목",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "SCHEDULE404_1",
                                      "message": "존재하지 않는 버킷 항목입니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "일정을 만들 수 없는 버킷(SAVED 아님/이미 연결됨)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "SCHEDULE409_1",
                                      "message": "일정을 만들 수 없는 버킷 항목입니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<ScheduleResDTO.Created> createSchedule(
            @Parameter(hidden = true) Long memberId,
            @Parameter(description = "버킷 항목 ID", example = "42") Long bucketItemId,
            @Valid ScheduleReqDTO.Create request
    );

    @Operation(
            summary = "일정 초대 수락/거절 API",
            description = """
                    # 일정 초대 수락/거절

                    ## 요청 형식
                    - 헤더: Authorization: Bearer {JWT 토큰}
                    - Path: scheduleId
                    - Body: action (ACCEPT / DECLINE)

                    초대받은 가족 구성원이 일정 초대에 수락 또는 거절로 응답합니다.

                    ## 규칙
                    - 초대받은(참여 레코드가 있는) 구성원만 응답할 수 있습니다. 제안자와 초대받지 않은 구성원은 응답할 수 없습니다.
                    - 응답 마감 전에는 수락과 거절 사이에서 응답을 바꿀 수 있고, 마감 이후·취소·완료된 일정에는 응답할 수 없습니다.
                    - 재확인 필요 상태였던 응답은 이 응답으로 해제됩니다.
                    - 응답 후 일정 전체 상태를 다시 계산합니다. 제안자 외 수락자가 1명 이상이면 CONFIRMED, 마감 전 0명이면 PENDING_RESPONSE입니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "응답 처리 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "SCHEDULE200_2",
                                      "message": "일정 응답 처리 성공",
                                      "result": {
                                        "scheduleId": 100,
                                        "myStatus": "ACCEPTED",
                                        "status": "CONFIRMED"
                                      }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "action 누락 또는 잘못된 값",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "COMMON400_1",
                                      "message": "잘못된 요청입니다.",
                                      "result": { "action": "action은 필수입니다." }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "초대받지 않은 일정(제안자·비초대자 포함)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "SCHEDULE403_3",
                                      "message": "초대받은 일정에만 응답할 수 있습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 일정",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "SCHEDULE404_2",
                                      "message": "존재하지 않는 일정입니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "응답 마감 이후 또는 취소·완료된 일정",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "SCHEDULE409_2",
                                      "message": "응답할 수 없는 일정입니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<ScheduleResDTO.Responded> respondSchedule(
            @Parameter(hidden = true) Long memberId,
            @Parameter(description = "일정 ID", example = "100") Long scheduleId,
            @Valid ScheduleReqDTO.Response request
    );

    @Operation(
            summary = "일정 취소 API",
            description = """
                    # 일정 취소

                    ## 요청 형식
                    - 헤더: Authorization: Bearer {JWT 토큰}
                    - Path: scheduleId

                    일정 제안자가 확정 또는 대기 중인 일정을 취소합니다.

                    ## 규칙
                    - 제안자만 취소할 수 있습니다.
                    - 대기(PENDING_RESPONSE)·확정(CONFIRMED) 일정만 취소할 수 있고, 이미 취소·완료된 일정은 취소할 수 없습니다.
                    - 취소 시 일정은 CANCELLED가 되고, 연결된 버킷은 다시 SAVED가 되어 같은 버킷에서 새 일정을 만들 수 있습니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "취소 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "SCHEDULE200_3",
                                      "message": "일정 취소 성공",
                                      "result": {
                                        "scheduleId": 100,
                                        "status": "CANCELLED",
                                        "bucketItemId": 42
                                      }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "제안자가 아닌 회원",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "SCHEDULE403_4",
                                      "message": "일정 제안자만 수정·취소할 수 있습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 일정",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "SCHEDULE404_2",
                                      "message": "존재하지 않는 일정입니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "취소할 수 없는 상태(이미 취소·완료)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "SCHEDULE409_3",
                                      "message": "취소할 수 없는 일정입니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<ScheduleResDTO.Cancelled> cancelSchedule(
            @Parameter(hidden = true) Long memberId,
            @Parameter(description = "일정 ID", example = "100") Long scheduleId
    );

    @Operation(
            summary = "일정 수정 API",
            description = """
                    # 일정 수정

                    ## 요청 형식
                    - 헤더: Authorization: Bearer {JWT 토큰}
                    - Path: scheduleId
                    - Body: 날짜·시간(필수), 초대할 가족 구성원 목록(제안자 제외, 1명 이상), 가족에게 한 줄(선택)

                    일정 제안자가 기존 일정의 날짜·시간·참여자를 수정합니다. 장소는 버킷(미션)에 묶인 값이라 수정 대상이 아닙니다.

                    ## 규칙
                    - 제안자만 수정할 수 있습니다.
                    - 대기(PENDING_RESPONSE)·확정(CONFIRMED) 일정만 수정할 수 있고, 활동이 시작된 뒤에는 수정할 수 없습니다.
                    - 날짜·시간·참여자 중 하나라도 바뀌면 기존 수락자는 재확인 필요 상태가 되고, 다시 수락/거절로 응답해야 합니다.
                    - 새 응답 마감은 변경된 시작 시각 기준 24시간 전이며, 24시간 이하가 남았으면 시작 시각입니다.
                    - 재확인 필요 상태는 확정 계산에서 수락으로 세지 않으므로, 수정 직후 일정은 보통 PENDING_RESPONSE로 돌아갑니다.
                    - 새로 초대된 구성원은 미응답(PENDING)으로 추가되고, 기존 미응답자는 거절로 바뀌지 않습니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "수정 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "SCHEDULE200_4",
                                      "message": "일정 수정 성공",
                                      "result": {
                                        "scheduleId": 100,
                                        "scheduleDate": "2026-11-16",
                                        "scheduleTime": "15:00",
                                        "status": "PENDING_RESPONSE",
                                        "responseDeadline": "2026-11-15T15:00:00",
                                        "proposerId": 1,
                                        "participantMemberIds": [2, 3]
                                      }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류(날짜·시간 누락/지난 시각/운영기간 밖/초대 대상 오류)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "필수값 누락", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "COMMON400_1",
                                              "message": "잘못된 요청입니다.",
                                              "result": { "scheduleDate": "scheduleDate는 필수입니다." }
                                            }
                                            """),
                                    @ExampleObject(name = "지난 시각", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "SCHEDULE400_2",
                                              "message": "지난 시각으로는 일정을 만들 수 없습니다.",
                                              "result": null
                                            }
                                            """),
                                    @ExampleObject(name = "초대 대상 오류", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "SCHEDULE400_4",
                                              "message": "같은 가족 구성원만 초대할 수 있습니다.",
                                              "result": null
                                            }
                                            """)
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "제안자가 아닌 회원",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "SCHEDULE403_4",
                                      "message": "일정 제안자만 수정·취소할 수 있습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 일정",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "SCHEDULE404_2",
                                      "message": "존재하지 않는 일정입니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "수정할 수 없는 상태(취소·미확정·완료 또는 활동 시작 후)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "SCHEDULE409_4",
                                      "message": "수정할 수 없는 일정입니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<ScheduleResDTO.Updated> updateSchedule(
            @Parameter(hidden = true) Long memberId,
            @Parameter(description = "일정 ID", example = "100") Long scheduleId,
            @Valid ScheduleReqDTO.Update request
    );

    @Operation(
            summary = "컨디션 커스텀 적용 API",
            description = """
                    # 컨디션 커스텀 적용

                    ## 요청 형식
                    - 헤더: Authorization: Bearer {JWT 토큰}
                    - Path: scheduleId
                    - Body: 적용할 컨디션 조건 목록(필수, 빈 배열 가능)

                    컨디션 조정 화면에서 선택한 조건을 해당 일정에 저장합니다. 저장한 조건은 근처 놀거리 추천 정렬에 반영됩니다.

                    ## 규칙
                    - 요청한 목록으로 기존 조건을 덮어씁니다. 여러 개를 함께 선택할 수 있습니다.
                    - 빈 배열을 보내면 모든 조건을 해제합니다. conditions 필드를 생략하거나 null로 보내면 400입니다.
                    - 같은 조건을 중복으로 보내도 한 번만 저장됩니다.
                    - 조건 값: REDUCE_STEPS 걸음 수 낮추기 / MORE_REST 휴식 시간 늘리기 / LOWER_BUDGET 예산 낮추기 / AVOID_CROWD 사람 많은 곳 피하기
                    - 같은 가족이면 누구나 변경할 수 있으며, 다른 가족의 일정은 존재하지 않는 일정과 같이 404로 응답합니다.
                    - 대기(PENDING_RESPONSE)·확정(CONFIRMED) 일정만 변경할 수 있습니다.
                    - 일정 당일에만 변경할 수 있습니다. 당일에는 시작 시각이 지나도 변경할 수 있습니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "적용 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "SCHEDULE200_7",
                                      "message": "컨디션 커스텀 적용 성공",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "conditions 누락·null 원소(COMMON400_1) / 존재하지 않는 조건 값·JSON 형식 오류(COMMON400_1)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "conditions 누락", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "COMMON400_1",
                                              "message": "잘못된 요청입니다.",
                                              "result": { "conditions": "conditions는 필수입니다." }
                                            }
                                            """),
                                    @ExampleObject(name = "존재하지 않는 조건 값", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "COMMON400_1",
                                              "message": "잘못된 요청입니다.",
                                              "result": null
                                            }
                                            """)
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 - 토큰 미삽입(COMMON401_1) / 유효하지 않은 토큰(AUTH401_1) / 토큰 만료(AUTH401_2)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "토큰 미삽입", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "COMMON401_1",
                                              "message": "인증이 필요합니다.",
                                              "result": null
                                            }
                                            """),
                                    @ExampleObject(name = "유효하지 않은 토큰", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "AUTH401_1",
                                              "message": "유효하지 않은 토큰입니다.",
                                              "result": null
                                            }
                                            """),
                                    @ExampleObject(name = "토큰 만료", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "AUTH401_2",
                                              "message": "토큰이 만료되었습니다",
                                              "result": null
                                            }
                                            """)
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
                                    @ExampleObject(name = "회원 없음", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "MEMBER404_1",
                                              "message": "존재하지 않는 회원입니다.",
                                              "result": null
                                            }
                                            """),
                                    @ExampleObject(name = "일정 없음", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "SCHEDULE404_2",
                                              "message": "존재하지 않는 일정입니다.",
                                              "result": null
                                            }
                                            """)
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "컨디션을 변경할 수 없는 일정(취소·미확정·완료 또는 일정 당일이 아님)(SCHEDULE409_5)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "SCHEDULE409_5",
                                      "message": "일정 당일에만 컨디션을 변경할 수 있습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<Void> updateConditions(
            @Parameter(hidden = true) Long memberId,
            @Parameter(description = "일정 ID", example = "118") Long scheduleId,
            @Valid ScheduleReqDTO.UpdateConditions request
    );
}
