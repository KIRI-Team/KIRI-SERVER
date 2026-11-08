package com.kiri.server.domain.review.controller.docs;

import com.kiri.server.domain.family.dto.FamilyReqDTO;
import com.kiri.server.domain.family.dto.FamilyResDTO;
import com.kiri.server.domain.review.dto.ReviewReqDTO;
import com.kiri.server.domain.review.dto.ReviewResDTO;
import com.kiri.server.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "리뷰 API")
public interface ReviewControllerDocs {

    // 리뷰 작성
    @Operation(
            summary = "리뷰 작성 API",
            description = """
                    # 리뷰 작성
                    일정을 수행한 가족 구성원이 해당 일정에 대한 리뷰를 작성합니다.
                    별점, 리뷰 내용, 일정 재참여 의사와 최소 1장의 사진을 등록할 수 있습니다.
                    
                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                        - Content-Type: application/json
                    - **Body**
                        - starRating: 별점
                        - imageKeys: 리뷰에 등록할 이미지 Key 목록
                        - content: 리뷰 내용
                        - isRejoinIntended: 일정 재참여 의사
                    - **Path Variable**
                        - scheduleId: 리뷰를 작성할 일정 ID
                    
                    ## 동작 방식
                    1. Access Token을 통해 인증된 사용자를 확인합니다.
                    2. scheduleId를 기준으로 일정을 조회합니다.
                    3. 현재 사용자가 해당 일정의 참여자인지 확인합니다.
                    4. 해당 참여자가 이미 리뷰를 작성했는지 확인합니다.
                    5. 리뷰 내용, 별점, 재참여 의사 및 imageKeys를 검증합니다.
                    6. review를 생성합니다.
                    7. 각 imageKey의 업로드 파일을 확인하고 user/original/ 경로로 확정합니다.
                    8. 확정된 imageKey로 review_photo를 생성합니다.
                    9. 해당 일정의 상태를 COMPLETED로 변경하고 completed_at을 기록합니다.
                    10. 리뷰 작성과 일정 완료 처리를 하나의 트랜잭션으로 처리합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공 예시",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": true,
                                        "code": "REVIEW200_1",
                                        "message": "리뷰 작성이 완료되었습니다.",
                                        "result": {
                                            "reviewId": 1,
                                            "scheduleId": 1
                                        }
                                    }
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "필수값 누락, 별점 범위 오류, 리뷰 내용 길이 초과, 이미지 미등록 등",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "별점 누락",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "별점을 입력해주세요.",
                                                        "result": {
                                                            "starRating": "가족 이름은 필수입니다."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "별점 범위 오류",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "starRating": "별점은 1점 이상이어야 합니다."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "별점 범위 오류",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "starRating": "별점은 5점 이하이어야 합니다."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "리뷰 누락",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "content": "리뷰 내용을 입력해주세요."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "리뷰 내용 길이 초과",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "content": "리뷰 내용은 500자 이내로 입력해주세요."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "재참여 의사 여부 누락",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "isRejoinIntended": "재참여 의사 여부를 입력해주세요."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "이미지 미등록",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "imageKeys": "사진은 최소 1장 이상 등록해주세요."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "지원하지 않는 이미지 형식",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "IMAGE400_1",
                                                        "message": "지원하지 않는 이미지 형식입니다.",
                                                        "result": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "유효하지 않은 이미지 키 (형식 오류, 다른 회원의 키)",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "IMAGE400_2",
                                                        "message": "유효하지 않은 이미지 키입니다.",
                                                        "result": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "이미지 10MB 초과",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "IMAGE400_3",
                                                        "message": "이미지 용량이 너무 큽니다.",
                                                        "result": null
                                                    }
                                                    """
                                    )
                            }
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
                    responseCode = "403",
                    description = "현재 사용자가 해당 일정의 참여자가 아닌 경우",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": true,
                                        "code": "REVIEW403_1",
                                        "message": "해당 일정에 참여한 가족 구성원이 아닙니다.",
                                        "result": null
                                    }
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "사용자, 일정, 업로드된 이미지가 존재하지 않는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "memberId에 해당하는 회원 없음",
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
                                            name = "scheduleId에 해당하는 일정이 없는 경우",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "SCHEDULE404_1",
                                                        "message": "일정을 찾을 수 없습니다.",
                                                        "result": null
                                                    }
                                            """
                                    ),
                                    @ExampleObject(
                                            name = "imageKey의 파일이 S3에 업로드되지 않은 경우",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "IMAGE404_1",
                                                        "message": "업로드된 이미지를 찾을 수 없습니다.",
                                                        "result": null
                                                    }
                                            """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "리뷰를 작성할 수 없는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "동일한 참여자가 동일한 일정에 이미 리뷰를 작성한 경우",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "REVIEW409_1",
                                                        "message": "이미 리뷰를 작성했습니다.",
                                                        "result": null
                                                    }
                                            """
                                    ),
                                    @ExampleObject(
                                            name = "취소된 일정 등 리뷰 작성이 불가능한 상태인 경우",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "REVIEW409_2,
                                                        "message": "리뷰를 작성할 수 없는 일정입니다.",
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
                            examples = {
                                    @ExampleObject(
                                            name = "이미지 저장소 오류",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "IMAGE500_1",
                                                        "message": "이미지 저장소 처리 중 오류가 발생했습니다.",
                                                        "result": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "서버 내부 오류",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON500_1",
                                                        "message": "서버 내부 오류가 발생했습니다.",
                                                        "result": null
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
    })
    ApiResponse<ReviewResDTO.Create> createReview(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @PathVariable Long scheduleId, @Valid @RequestBody ReviewReqDTO.Create dto);

    // 리뷰(기록) 목록 조회
    @Operation(
            summary = "리뷰(기록) 목록 조회 API",
            description = """
                    # 리뷰(기록) 목록 조회
                    완료된 약속(일정) 하나에 대해, 해당 일정 참여자들이 작성한 리뷰를 목록으로 조회합니다.
                    기록 완료 화면 '가족 추억' 탭에서 사용합니다. 참여자 수가 가족 규모라 페이징 없이 전체를 작성순으로 반환합니다.

                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                    - **Path Variable**
                        - scheduleId: 리뷰를 조회할 일정 ID

                    ## 동작 방식
                    1. Access Token으로 인증된 사용자와 그 가족을 확인합니다.
                    2. scheduleId로 일정을 조회하고, 요청자 가족의 일정인지 검증합니다.
                    3. 해당 일정의 리뷰를 작성순으로 조회합니다. (작성자·사진 포함)
                    4. 각 리뷰의 imageKey를 이미지 URL로 변환하여 반환합니다.
                    5. isMine으로 본인 작성 리뷰(수정하기 노출용)를 구분합니다.

                    ## 참고
                    - 상단 활동 정보(제목·시작시간·장소 등)는 일정 상세 조회 API 소관으로, 이 API에는 포함되지 않습니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공 (리뷰가 없으면 reviews 빈 배열)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": true,
                                        "code": "REVIEW200_2",
                                        "message": "리뷰 목록 조회가 완료되었습니다.",
                                        "result": {
                                            "scheduleId": 10,
                                            "reviews": [
                                                {
                                                    "reviewId": 1,
                                                    "writer": {
                                                        "memberId": 5,
                                                        "nickname": "김주연",
                                                        "familyRole": "DAUGHTER"
                                                    },
                                                    "content": "아빠가 자주 가는 식당에 처음 가봤다.",
                                                    "starRating": 5,
                                                    "isRejoinIntended": true,
                                                    "imageUrls": [
                                                        "https://cdn.example.com/review/1/image-1.jpg",
                                                        "https://cdn.example.com/review/1/image-2.jpg"
                                                    ],
                                                    "createdAt": "2026-06-13T14:50:00",
                                                    "isMine": true
                                                }
                                            ]
                                        }
                                    }
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 (Authorization 헤더 누락, 유효하지 않은/만료된 토큰)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": false,
                                        "code": "COMMON401_1",
                                        "message": "인증이 필요합니다.",
                                        "result": null
                                    }
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "요청자 가족의 일정이 아닌 경우(가족 공간 없음 포함)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": false,
                                        "code": "REVIEW403_2",
                                        "message": "해당 일정의 리뷰를 조회할 권한이 없습니다.",
                                        "result": null
                                    }
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "사용자나 일정이 존재하지 않는 경우",
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
    ApiResponse<ReviewResDTO.ScheduleReviews> getScheduleReviews(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @PathVariable Long scheduleId);
}
