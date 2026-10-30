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
                                      "message": "일정 제안자만 취소할 수 있습니다.",
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
}
