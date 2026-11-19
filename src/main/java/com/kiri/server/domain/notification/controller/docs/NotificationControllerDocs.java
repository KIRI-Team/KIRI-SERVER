package com.kiri.server.domain.notification.controller.docs;

import com.kiri.server.domain.notification.dto.NotificationResDTO;
import com.kiri.server.domain.notification.enums.NotificationCategory;
import com.kiri.server.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "알림 API", description = "알림함 조회·미읽음 수·읽음 처리. 알림함에는 생성 후 90일 이내 알림만 보입니다.")
public interface NotificationControllerDocs {

    String UNAUTHORIZED_NO_HEADER = """
            {
                "isSuccess": false,
                "code": "COMMON401_1",
                "message": "인증이 필요합니다.",
                "result": null
            }
            """;

    String UNAUTHORIZED_INVALID = """
            {
                "isSuccess": false,
                "code": "AUTH401_1",
                "message": "유효하지 않은 토큰입니다.",
                "result": null
            }
            """;

    String UNAUTHORIZED_EXPIRED = """
            {
                "isSuccess": false,
                "code": "AUTH401_2",
                "message": "토큰이 만료되었습니다.",
                "result": null
            }
            """;

    String SERVER_ERROR = """
            {
                "isSuccess": false,
                "code": "COMMON500_1",
                "message": "서버 내부 오류가 발생했습니다.",
                "result": null
            }
            """;

    @Operation(
            summary = "알림 목록 조회 API",
            description = """
                    # 알림 목록 조회
                    로그인한 회원의 알림함을 최신순으로 조회합니다.

                    ## 요청 형식
                    - **Header**: Authorization: Bearer {Access_Token}
                    - **Query**
                        - category : 알림함 탭. 생략하면 **전체** 탭입니다.
                            - SCHEDULE(일정) / POKE(콕) / RECORD(기록) / MISSION(미션) / QUESTION(질문)
                        - page : 페이지 번호 (0부터 시작)
                        - size : 페이지 크기 (기본값 20)

                    ## 동작 방식
                    1. 생성 후 90일 이내 알림만 보여줍니다. 90일이 지난 알림은 매일 새벽 정리 작업으로 삭제됩니다.
                    2. 생성일시 내림차순, 같은 시각이면 알림 ID 내림차순으로 정렬합니다. sort 파라미터는 무시합니다.
                    3. 목록을 열거나 스크롤하는 것만으로는 읽음 처리되지 않습니다. 알림을 눌렀을 때 읽음 처리 API를 호출하세요.

                    ## 알림을 눌렀을 때 이동할 화면 (targetType)
                    | targetType | 화면 | referenceId | referenceDate |
                    | --- | --- | --- | --- |
                    | QUESTION | 해당 날짜의 가족 질문 | familyQuestionId | 질문 날짜 |
                    | FAMILY_ANSWERS | 가족 답변 모아보기 | familyQuestionId | 질문 날짜 |
                    | WEEKLY_MISSION | 홈 주간 미션 카드 | missionId | 주간 미션 시작일 |
                    | SCHEDULE_INVITATION | 일정 초대 상세 | scheduleId | 일정 날짜 |
                    | SCHEDULE | 일정 상세 | scheduleId | 일정 날짜 |
                    | REVIEW_WRITE | 리뷰 작성 | scheduleId | 일정 날짜 |
                    | SCHEDULE_RECORD | 완료 활동 기록 | scheduleId | 일정 날짜 |
                    | WEEKLY_MISSION_RECORD | 주간 미션 완료 기록 | familyMissionRecommendationId | 미션 시작일 |
                    | MISSION | 미션 상세 (외부 행사 취소 안내) | missionId | 없음 |

                    대상이 삭제됐거나 가족을 떠나 접근 권한이 없으면 대상 API가 404·403을 돌려줍니다.
                    이때 앱은 "더 이상 확인할 수 없는 내용이에요." 안내를 보여주세요.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "알림 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": true,
                                        "code": "NOTIFICATION200_1",
                                        "message": "알림 목록 조회에 성공했습니다.",
                                        "result": {
                                            "content": [
                                                {
                                                    "notificationId": 31,
                                                    "title": "초대장이 도착했어요! 💌",
                                                    "body": "엄마님이 '서울숲 산책하기'에 초대했어요. 11월 22일 14:00에 함께할지 알려주세요.",
                                                    "notificationType": "SCHEDULE_INVITATION",
                                                    "category": "SCHEDULE",
                                                    "targetType": "SCHEDULE_INVITATION",
                                                    "isRead": false,
                                                    "referenceId": 10,
                                                    "referenceDate": "2026-11-22",
                                                    "createdAt": "2026-11-17T21:00:00"
                                                }
                                            ],
                                            "totalElements": 1,
                                            "totalPages": 1,
                                            "size": 20,
                                            "number": 0,
                                            "numberOfElements": 1,
                                            "first": true,
                                            "last": true,
                                            "empty": false
                                        }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "category 값이 올바르지 않음",
                    content = @Content(mediaType = "application/json")
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "Authorization 헤더 없음", value = UNAUTHORIZED_NO_HEADER),
                                    @ExampleObject(name = "위조·형식 오류·refresh 토큰 사용", value = UNAUTHORIZED_INVALID),
                                    @ExampleObject(name = "accessToken 만료", value = UNAUTHORIZED_EXPIRED)
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 오류",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = SERVER_ERROR))
            )
    })
    ApiResponse<Page<NotificationResDTO.NotificationPreview>> getNotifications(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "알림함 탭. 생략하면 전체", example = "SCHEDULE") NotificationCategory category,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable);

    @Operation(
            summary = "읽지 않은 알림 수 조회 API",
            description = """
                    # 읽지 않은 알림 수 조회
                    알림함 헤더에 표시할 미읽음 알림 수를 조회합니다. 선택한 탭과 관계없이 전체 기준입니다.

                    ## 응답
                    - unreadCount : 최근 90일 안의 읽지 않은 알림 수
                    - badge : 헤더에 그대로 표시할 값. 0개면 null(숫자 숨김), 1~99개면 숫자, 100개 이상이면 "99+"
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "미읽음 수 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": true,
                                        "code": "NOTIFICATION200_3",
                                        "message": "읽지 않은 알림 수 조회에 성공했습니다.",
                                        "result": {
                                            "unreadCount": 120,
                                            "badge": "99+"
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
                            examples = {
                                    @ExampleObject(name = "Authorization 헤더 없음", value = UNAUTHORIZED_NO_HEADER),
                                    @ExampleObject(name = "위조·형식 오류·refresh 토큰 사용", value = UNAUTHORIZED_INVALID),
                                    @ExampleObject(name = "accessToken 만료", value = UNAUTHORIZED_EXPIRED)
                            }
                    )
            )
    })
    ApiResponse<NotificationResDTO.UnreadCount> getUnreadCount(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId);

    @Operation(
            summary = "모든 알림 읽음 처리 API",
            description = """
                    # 모두 읽음
                    회원의 읽지 않은 알림을 모두 읽음 처리합니다.
                    **현재 선택한 탭과 관계없이 전체 알림함**이 대상입니다. 앱에서 확인 메시지를 보여준 뒤 호출하세요.

                    ## 응답
                    - readCount : 이번 요청으로 읽음 처리된 알림 수
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "모두 읽음 처리 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": true,
                                        "code": "NOTIFICATION200_4",
                                        "message": "모든 알림 읽음 처리에 성공했습니다.",
                                        "result": {
                                            "readCount": 5
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
                            examples = {
                                    @ExampleObject(name = "Authorization 헤더 없음", value = UNAUTHORIZED_NO_HEADER),
                                    @ExampleObject(name = "위조·형식 오류·refresh 토큰 사용", value = UNAUTHORIZED_INVALID),
                                    @ExampleObject(name = "accessToken 만료", value = UNAUTHORIZED_EXPIRED)
                            }
                    )
            )
    })
    ApiResponse<NotificationResDTO.ReadAllResult> markAllNotificationsAsRead(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId);

    @Operation(
            summary = "알림 읽음 처리 API",
            description = """
                    # 알림 읽음 처리
                    사용자가 알림을 눌렀을 때 호출합니다. 읽음 처리 후 targetType에 맞는 화면으로 이동하세요.

                    ## 요청 형식
                    - **Header**: Authorization: Bearer {Access_Token}
                    - **Path**: notificationId : 읽음 처리할 알림 ID

                    ## 동작 방식
                    1. 본인의 알림이 아니거나 없는 알림이면 404를 돌려줍니다.
                    2. 이미 읽은 알림을 다시 호출해도 성공합니다.
                    3. 읽음 여부로 일정 초대 등의 유효성을 판단하지 않습니다. 이미 읽은 초대도 응답 마감 전이면 수락·거절할 수 있습니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "알림 읽음 처리 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": true,
                                        "code": "NOTIFICATION200_2",
                                        "message": "알림 읽음 처리에 성공했습니다.",
                                        "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "Authorization 헤더 없음", value = UNAUTHORIZED_NO_HEADER),
                                    @ExampleObject(name = "위조·형식 오류·refresh 토큰 사용", value = UNAUTHORIZED_INVALID),
                                    @ExampleObject(name = "accessToken 만료", value = UNAUTHORIZED_EXPIRED)
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "알림이 없거나 본인의 알림이 아님",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": false,
                                        "code": "NOTIFICATION404_1",
                                        "message": "알림을 찾을 수 없습니다.",
                                        "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<Void> markNotificationAsRead(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "읽음 처리할 알림 ID", example = "1") Long notificationId);
}
