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
}
