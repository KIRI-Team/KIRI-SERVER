package com.kiri.server.domain.notification.controller.docs;

import com.kiri.server.domain.notification.dto.NotificationResDTO;
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

@Tag(name = "알림 API")
public interface NotificationControllerDocs {

    // 알림 목록 조회
    @Operation(
            summary = "알림 목록 조회 API",
            description = """
                    # 알림 목록 조회
                    로그인한 회원에게 발송된 알림 목록을 최신순으로 조회합니다.

                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                    - **Query**
                        - page : 조회할 페이지 번호 (0부터 시작)
                        - size : 한 페이지에 조회할 알림 개수 (기본값 20)

                    ## 동작 방식
                    1. Access Token을 통해 인증된 회원을 확인합니다.
                    2. 인증된 회원에게 생성된 알림 목록을 조회합니다.
                    3. 알림은 생성일시 기준 내림차순으로 정렬합니다.
                    4. 페이지네이션을 적용하여 알림 목록을 반환합니다.
                    5. 각 알림의 읽음 여부, 알림 유형, 참조 ID 및 생성일시를 함께 반환합니다.
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
                                                    "notificationId": 1,
                                                    "title": "새로운 일정 초대",
                                                    "body": "가족이 새로운 일정에 초대했어요.",
                                                    "notificationType": "SCHEDULE_INVITATION",
                                                    "isRead": false,
                                                    "referenceId": 10,
                                                    "createdAt": "2026-11-15T21:00:00"
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
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 (Authorization 헤더 누락, 유효하지 않은 토큰, 만료된 토큰)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "Authorization 헤더 없음",
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
                                            name = "위조·형식 오류·refresh 토큰 사용",
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
                                            name = "accessToken 만료",
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
                    responseCode = "500",
                    description = "서버 오류",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": false,
                                        "code": "COMMON500_1",
                                        "message": "서버 내부 오류가 발생했습니다.",
                                        "result": null
                                    }
                                    """
                            )
                    )
            )
    })
    ApiResponse<Page<NotificationResDTO.NotificationPreview>> getNotifications(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @ParameterObject @PageableDefault(size = 20) Pageable pageable);

    // 알림 읽음 처리
    @Operation(
            summary = "알림 읽음 처리 API",
            description = """
                    # 알림 읽음 처리
                    로그인한 회원의 특정 알림을 읽음 상태로 변경합니다.

                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                    - **Path**
                        - notificationId : 읽음 처리할 알림 ID

                    ## 동작 방식
                    1. Access Token을 통해 인증된 회원을 확인합니다.
                    2. notificationId와 인증된 회원 ID를 기준으로 알림을 조회합니다.
                    3. 해당 알림이 존재하지 않거나 다른 회원의 알림인 경우 예외를 반환합니다.
                    4. 본인의 알림이면 읽음 상태(isRead)를 true로 변경합니다.
                    5. 읽음 처리가 완료되면 성공 응답을 반환합니다.
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
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 (Authorization 헤더 누락, 유효하지 않은 토큰, 만료된 토큰)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "Authorization 헤더 없음",
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
                                            name = "위조·형식 오류·refresh 토큰 사용",
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
                                            name = "accessToken 만료",
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
                    responseCode = "404",
                    description = "알림이 존재하지 않거나 본인의 알림이 아닌 경우",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": false,
                                        "code": "NOTIFICATION404_1",
                                        "message": "알림을 찾을 수 없습니다.",
                                        "result": null
                                    }
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 오류",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": false,
                                        "code": "COMMON500_1",
                                        "message": "서버 내부 오류가 발생했습니다.",
                                        "result": null
                                    }
                                    """
                            )
                    )
            )
    })
    ApiResponse<Void> markNotificationAsRead(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @Parameter(description = "읽음 처리할 알림 ID", example = "1") Long notificationId);
}
