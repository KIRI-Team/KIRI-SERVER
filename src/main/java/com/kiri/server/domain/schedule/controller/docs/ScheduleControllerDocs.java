package com.kiri.server.domain.schedule.controller.docs;

import com.kiri.server.domain.schedule.dto.ScheduleReqDTO;
import com.kiri.server.domain.mission.dto.MissionResDTO;
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

import java.util.List;

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
                    - reviewImageUrl: 해당 일정의 대표사진 URL(서명 URL, 만료 시간 있음). 대표사진을 직접 지정하지 않았으면 작성순으로 가장 빠른 리뷰의 첫 사진이며, 리뷰·사진이 없으면 null 입니다. (프론트에서 기본 완료 마커로 처리)
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
                                            "status": "COMPLETED",
                                            "myStatus": "ACCEPTED",
                                            "reviewImageUrl": "https://cdn.example.com/review/100/a.jpg?Signature=..."
                                          },
                                          {
                                            "scheduleId": 101,
                                            "date": "2026-10-24",
                                            "time": "10:00",
                                            "status": "PENDING_RESPONSE",
                                            "myStatus": "NOT_INVITED",
                                            "reviewImageUrl": null
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
                    - missionTitle, walkingLevel, duration*: 일정에 연결된 미션의 값 (소요 시간은 min~max로 표시)
                    - walkingLevel은 예상 걷기량으로 LOW(적음)·MEDIUM(보통)·HIGH(많음)이며, 판단할 정보가 없으면 null입니다.
                    - conditions: 현재 일정에 적용된 조건 목록. 적용한 조건이 없으면 빈 배열입니다.
                      (REDUCE_STEPS 걸음 수 낮추기 / MORE_REST 휴식 시간 늘리기 / LOWER_BUDGET 예산 낮추기 / AVOID_CROWD 사람 많은 곳 피하기)
                    - 같은 가족의 일정만 조회할 수 있으며, 다른 가족의 일정은 존재하지 않는 일정과 같이 404로 응답합니다.
                    - 약속 당일 화면 기능이라 수락한 최종 참여자만 조회할 수 있습니다. (응답 대기·거절·재확인 필요·초대 안 됨은 403)
                    - 확정(CONFIRMED)된 일정의 당일에만 조회할 수 있습니다. (그 외 409)
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
                                                        "walkingLevel": "MEDIUM",
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
                                                        "walkingLevel": "MEDIUM",
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
                    description = "가족공간이 없는 회원(SCHEDULE403_1) / 약속의 최종 참여자가 아님(SCHEDULE403_5)",
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
                                    @ExampleObject(name = "최종 참여자 아님", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "SCHEDULE403_5",
                                              "message": "약속에 참여하는 가족만 이용할 수 있습니다.",
                                              "result": null
                                            }
                                            """)
                            }
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
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "확정되지 않았거나 일정 당일이 아님(SCHEDULE409_6)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "SCHEDULE409_6",
                                      "message": "일정 당일에만 컨디션을 조회할 수 있습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<ScheduleResDTO.Conditions> getConditions(
            @Parameter(hidden = true) Long memberId,
            @Parameter(description = "일정 ID", example = "118") Long scheduleId
    );

    @Operation(
            summary = "일정 상세 근처 놀거리 조회 API",
            description = """
                    # 일정 상세 근처 놀거리 조회

                    ## 요청 형식
                    - 헤더: Authorization: Bearer {JWT 토큰}
                    - Path: scheduleId

                    일정의 메인 미션 장소를 기준으로 반경 5km 안에서 함께 들르기 좋은 장소를 추천합니다.
                    사용자 GPS가 아니라 일정 장소 좌표가 기준입니다.

                    ## 응답 규칙
                    - 활성(ACTIVE) 상태이고 일정 날짜에 운영 중인 미션이 있는 장소만 추천합니다. 메인 미션 장소는 제외합니다.
                    - 정렬: 취향 점수 + 컨디션 점수가 높은 순 → 같으면 거리가 가까운 순
                      - 취향 점수: 제안자와 수락한 참여자의 취향 점수 평균
                        취향 테스트·취향 질문을 우선하고 버킷 저장·후기 이력을 보조로 반영하며, 가족 미션 추천과 같은 산식을 사용합니다.
                      - 컨디션 점수: 해당 일정에 저장된 조건만 반영
                        - REDUCE_STEPS: 가까울수록 가산
                        - MORE_REST: FOOD_CAFE·실내 장소 가산 (실내·외 겸용은 절반)
                        - LOWER_BUDGET: 1인 기준 무료 1점 / 1만 원 이하 0.5점 / 1만 원 초과 0점 / 요금 정보가 없으면 0점
                        - AVOID_CROWD: 혼잡 데이터가 없어 현재는 반영하지 않음
                    - 같은 장소에 미션이 여러 개면 장소 하나로 묶습니다.
                    - 정렬된 장소 중 상위 5개만 반환합니다.
                    - region: 주소의 시·도와 시·군·구까지만 축약해 반환합니다. (예: 서울특별시 서초구 → 서울시 서초구, 세종특별자치시 → 세종시)
                    - description: 카드 하단 문구로, 추천 근거 하나를 아래 순서로 반환합니다.
                      1. 제안자·수락자가 취향 테스트에서 고른 카테고리 → "우리 가족이 좋아하는 활동이에요"
                      2. 휴식 조건 적용 + FOOD_CAFE·실내 장소 → "잠깐 쉬어가기 좋은 곳이에요"
                      3. 일정 장소에서 1km 이내 → "일정 장소에서 가까운 곳이에요"
                      근거가 없으면 미션 소개 문구를 반환합니다.
                    - imageUrl: 장소 이미지가 없으면 미션 이미지, 둘 다 없으면 null입니다.
                    - price: 1인 기준 요금(원). 무료면 0, 1인 기준 가격을 확인할 수 없으면 null이며 "정보 확인 필요"로 표시합니다.
                    - missionId: 카드를 누르면 이 ID로 미션 상세 화면으로 이동합니다.
                    - distance: 일정 장소로부터의 거리(km, 소수 첫째 자리)
                    - 추천할 장소가 없으면 빈 배열입니다.
                    - 같은 가족의 일정만 조회할 수 있으며, 다른 가족의 일정은 존재하지 않는 일정과 같이 404로 응답합니다.
                    - 취소된 일정은 조회할 수 없습니다. (409)
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
                                            name = "추천 장소 있음",
                                            value = """
                                                    {
                                                      "isSuccess": true,
                                                      "code": "SCHEDULE200_8",
                                                      "message": "근처 놀거리 추천 조회 성공",
                                                      "result": [
                                                        {
                                                          "missionId": 241,
                                                          "placeName": "세빛섬",
                                                          "region": "서울시 서초구",
                                                          "description": "일정 장소에서 가까운 곳이에요",
                                                          "imageUrl": "https://tong.visitkorea.or.kr/cms/resource/00/1234500_image2_1.jpg",
                                                          "price": 0,
                                                          "distance": 0.8
                                                        }
                                                      ]
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "추천 장소 없음",
                                            value = """
                                                    {
                                                      "isSuccess": true,
                                                      "code": "SCHEDULE200_8",
                                                      "message": "근처 놀거리 추천 조회 성공",
                                                      "result": []
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
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "취소된 일정(SCHEDULE409_7)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "SCHEDULE409_7",
                                      "message": "취소된 일정입니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<List<MissionResDTO.NearbyItem>> getNearby(
            @Parameter(hidden = true) Long memberId,
            @Parameter(description = "일정 ID", example = "118") Long scheduleId
    );

    @Operation(
            summary = "일정 상세 맞춤템 조회 API",
            description = """
                    # 일정 상세 맞춤템 조회

                    ## 요청 형식
                    - 헤더: Authorization: Bearer {JWT 토큰}
                    - Path: scheduleId

                    약속 당일 화면의 맞춤템입니다. 미션에 저장된 준비물에 일정 장소·시간 날씨 항목을 더해 반환합니다.
                    날씨는 사용자 GPS가 아니라 일정 장소 좌표와 일정 시간 기준입니다.

                    ## 응답 규칙
                    - 응답 형식은 미션 상세 맞춤템 조회 API와 같습니다. 최대 4개, 없으면 빈 배열입니다.
                    - 순서: 공식 준비물 → 날씨 항목 → 활동 편의 항목(편한 신발·물 등) → 혜택 물품
                    - 날씨 항목: 야외·실내외 겸용 미션에만, 일정 시작부터 미션 최대 소요시간(없으면 3시간) 구간 예보로 판단
                      - 우산: 비·눈 예보가 있거나 강수확률 60% 이상
                      - 모자/양산: 최고 기온 28도 이상
                      - 겉옷·방한용품: 최저 기온 5도 이하
                      - description에 추천 이유를 담습니다. (예: "강수확률이 70%예요.")
                    - 일정 당일에는 그날 02시 발표 예보로 고정해 하루 동안 결과가 바뀌지 않습니다. 당일 전에는 최신 예보를 씁니다.
                    - 다음 경우 날씨 항목 없이 미션 준비물만 반환합니다.
                      - 예보 범위(약 3~4일) 밖이거나 지난 일정·완료된 일정
                      - 날씨 조회 실패
                    - 같은 가족의 일정만 조회할 수 있으며, 다른 가족의 일정은 존재하지 않는 일정과 같이 404로 응답합니다.
                    - 취소된 일정은 조회할 수 없습니다. (409)
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
                                            name = "날씨 항목 포함",
                                            value = """
                                                    {
                                                      "isSuccess": true,
                                                      "code": "SCHEDULE200_10",
                                                      "message": "일정 맞춤템 조회 성공",
                                                      "result": [
                                                        {
                                                          "title": "신분증",
                                                          "description": "공식 안내에 있는 준비물이에요."
                                                        },
                                                        {
                                                          "title": "우산",
                                                          "description": "강수확률이 70%예요."
                                                        },
                                                        {
                                                          "title": "편한 신발",
                                                          "description": "많이 걷는 일정이에요."
                                                        }
                                                      ]
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "맞춤템 없음",
                                            value = """
                                                    {
                                                      "isSuccess": true,
                                                      "code": "SCHEDULE200_10",
                                                      "message": "일정 맞춤템 조회 성공",
                                                      "result": []
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
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "취소된 일정(SCHEDULE409_7)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "SCHEDULE409_7",
                                      "message": "취소된 일정입니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<List<MissionResDTO.ChecklistItem>> getEssentials(
            @Parameter(hidden = true) Long memberId,
            @Parameter(description = "일정 ID", example = "118") Long scheduleId
    );

    @Operation(
            summary = "일정 상세 조회 API",
            description = """
                    # 일정 상세 조회

                    ## 요청 형식
                    - 헤더: Authorization: Bearer {JWT 토큰}
                    - Path: scheduleId

                    일정의 날짜, 시작 시간, 일정 상태, 초대 정보(제안자, 가족에게 한 줄)와 참여자별 수락 여부를 조회합니다.
                    초대받은 화면, 약속 확정·당일 화면, 일정 취소 확인 화면 모두 이 API로 처리합니다.
                    미션 정보(제목, 행사 설명, 장소, 걸음 수, 활동 시간)는 미션 상세 조회 API를 사용합니다.

                    ## 응답 규칙
                    - date: yyyy-MM-dd, time: HH:mm
                    - status: 화면 표시용 일정 상태
                      - SCHEDULED: 약속 예정 (응답 대기 / 미확정)
                      - CONFIRMED: 약속 확정 (확정, 일정 당일이 아님)
                      - TODAY: 약속 당일이에요! (확정, 일정 날짜가 오늘)
                      - COMPLETED: 약속 완료
                    - proposer: 일정을 제안한 구성원. 탈퇴한 회원은 nickname이 "탈퇴한 사용자", familyRole이 null입니다.
                    - requestMessage: 가족에게 한 줄. 입력하지 않았으면 null입니다.
                    - participants: 제안자가 맨 앞이며, 이후 초대 순서입니다.
                      - nickname: 탈퇴한 회원은 "탈퇴한 사용자"로 표시합니다.
                      - accepted: 수락했으면 true, 응답 대기·거절이면 false
                    - 같은 가족의 일정이면 초대받지 않은 구성원도 조회할 수 있으며, 다른 가족의 일정은 존재하지 않는 일정과 같이 404로 응답합니다.
                    - 취소된 일정은 조회할 수 없습니다. (409)
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
                                      "code": "SCHEDULE200_9",
                                      "message": "일정 상세 조회 성공",
                                      "result": {
                                        "missionId": 42,
                                        "date": "2026-11-17",
                                        "time": "14:50",
                                        "status": "SCHEDULED",
                                        "proposer": {
                                          "memberId": 3,
                                          "nickname": "첫째",
                                          "familyRole": "DAUGHTER"
                                        },
                                        "requestMessage": "엄마, 아빠 나 여기 가고싶은데 같이 갈래요??",
                                        "participants": [
                                          { "nickname": "첫째", "accepted": true },
                                          { "nickname": "엄마", "accepted": false },
                                          { "nickname": "아빠", "accepted": false }
                                        ]
                                      }
                                    }
                                    """)
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
                                                      "message": "토큰이 만료되었습니다.",
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
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "취소된 일정(SCHEDULE409_7)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "SCHEDULE409_7",
                                      "message": "취소된 일정입니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<ScheduleResDTO.Detail> getDetail(
            @Parameter(hidden = true) Long memberId,
            @Parameter(description = "일정 ID", example = "118") Long scheduleId
    );
}
