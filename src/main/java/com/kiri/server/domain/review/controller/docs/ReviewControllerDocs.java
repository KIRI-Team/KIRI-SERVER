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
                    4. 각 리뷰의 사진을 표시 순서대로 사진 ID, imageKey, 이미지 URL(서명 URL)로 반환합니다.
                       리뷰 수정 시 유지할 사진은 이 imageKey를 그대로 imageKeys에 전달합니다.
                    5. representativePhotoId로 현재 대표사진의 사진 ID를 반환합니다.
                       대표사진을 직접 지정하지 않았으면 작성순으로 가장 빠른 리뷰의 첫 번째 사진이며, 사진이 하나도 없으면 null입니다.
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
                                            "representativePhotoId": 1,
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
                                                    "photos": [
                                                        {
                                                            "reviewPhotoId": 1,
                                                            "imageKey": "user/original/5/3f2b8c1e-1a2b-4c3d-9e8f-1234567890ab.jpg",
                                                            "imageUrl": "https://cdn.example.com/user/original/5/3f2b8c1e-1a2b-4c3d-9e8f-1234567890ab.jpg?Expires=...&Signature=...&Key-Pair-Id=..."
                                                        },
                                                        {
                                                            "reviewPhotoId": 2,
                                                            "imageKey": "user/original/5/7a6b5c4d-2b3c-4d5e-8f9a-abcdef123456.jpg",
                                                            "imageUrl": "https://cdn.example.com/user/original/5/7a6b5c4d-2b3c-4d5e-8f9a-abcdef123456.jpg?Expires=...&Signature=...&Key-Pair-Id=..."
                                                        }
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

    // 리뷰 수정
    @Operation(
            summary = "리뷰 수정 API",
            description = """
                    # 리뷰 수정
                    본인이 작성한 리뷰의 내용, 별점, 재참여 의사, 사진을 수정합니다.
                    수정 화면의 최종 상태를 모두 보내는 전체 교체 방식이며, 요청 형식과 검증 규칙은 리뷰 작성과 같습니다.

                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                        - Content-Type: application/json
                    - **Body**
                        - starRating: 별점
                        - imageKeys: 수정 후 최종 사진 Key 목록 (표시 순서대로)
                            - 유지할 기존 사진: 조회한 기존 Key(user/original/...)를 그대로 전달
                            - 새 사진: Presigned URL로 업로드한 Key(pending/...)를 전달
                            - 목록의 순서가 그대로 사진 표시 순서가 됩니다.
                        - content: 리뷰 내용
                        - isRejoinIntended: 일정 재참여 의사
                    - **Path Variable**
                        - scheduleId: 리뷰가 속한 일정 ID
                        - reviewId: 수정할 리뷰 ID

                    ## 동작 방식
                    1. Access Token을 통해 인증된 사용자를 확인합니다.
                    2. scheduleId, reviewId로 리뷰를 조회합니다. 해당 일정의 리뷰가 아니면 404로 응답합니다.
                    3. 본인이 작성한 리뷰인지 확인합니다.
                    4. 리뷰 내용, 별점, 재참여 의사를 요청 값으로 덮어씁니다.
                    5. 기존 사진 중 imageKeys에 있는 사진은 유지하고 표시 순서만 갱신합니다.
                    6. 새 Key는 업로드 파일을 확인한 뒤 user/original/ 경로로 확정하고 review_photo를 생성합니다.
                    7. imageKeys에서 빠진 기존 사진은 review_photo를 삭제하고, 트랜잭션 커밋 후 S3에서도 삭제합니다.
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
                                        "code": "REVIEW200_3",
                                        "message": "리뷰 수정이 완료되었습니다.",
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
                    description = "요청 값 검증 실패, 이미지 오류, 경로 변수·Body 형식 오류",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "별점 누락",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "starRating": "별점을 입력해주세요."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "별점 범위 오류 (1~5)",
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
                                            name = "리뷰 내용 누락",
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
                                            name = "Body 형식 오류 (JSON 파싱 실패)",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": "요청 Body 형식이 잘못되었습니다."
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "경로 변수 형식 오류 (scheduleId, reviewId가 숫자가 아님)",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "지원하지 않는 이미지 형식 (새 사진)",
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
                                            name = "유효하지 않은 이미지 키 (형식 오류, 다른 회원의 키, 이 리뷰에 없던 기존 키)",
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
                                            name = "이미지 10MB 초과 (새 사진)",
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
                    description = "다른 가족의 리뷰이거나(가족 공간 없음 포함) 본인이 작성한 리뷰가 아닌 경우",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "다른 가족의 리뷰",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "REVIEW403_2",
                                                        "message": "해당 일정의 리뷰를 조회할 권한이 없습니다.",
                                                        "result": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "같은 가족이지만 본인이 작성한 리뷰가 아님",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "REVIEW403_3",
                                                        "message": "본인이 작성한 리뷰만 수정·삭제할 수 있습니다.",
                                                        "result": null
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "리뷰, 사용자, 업로드된 이미지가 존재하지 않는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "리뷰 없음 (해당 일정의 리뷰가 아닌 경우 포함)",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "REVIEW404_1",
                                                        "message": "존재하지 않는 리뷰입니다.",
                                                        "result": null
                                                    }
                                                    """
                                    ),
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
                                            name = "새 imageKey의 파일이 S3에 업로드되지 않은 경우",
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
            )
    })
    ApiResponse<ReviewResDTO.Create> updateReview(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @PathVariable Long scheduleId, @PathVariable Long reviewId, @Valid @RequestBody ReviewReqDTO.Create dto);

    // 리뷰 삭제
    @Operation(
            summary = "리뷰 삭제 API",
            description = """
                    # 리뷰 삭제
                    본인이 작성한 리뷰와 리뷰 사진을 삭제합니다.

                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                    - **Path Variable**
                        - scheduleId: 리뷰가 속한 일정 ID
                        - reviewId: 삭제할 리뷰 ID

                    ## 동작 방식
                    1. Access Token을 통해 인증된 사용자를 확인합니다.
                    2. scheduleId, reviewId로 리뷰를 조회합니다. 해당 일정의 리뷰가 아니면 404로 응답합니다.
                    3. 본인이 작성한 리뷰인지 확인합니다.
                    4. review_photo와 review를 삭제합니다.
                    5. 삭제한 사진은 트랜잭션 커밋 후 S3에서 삭제합니다.

                    ## 참고
                    - 리뷰를 삭제해도 일정의 완료(COMPLETED) 상태는 유지됩니다.
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
                                        "code": "REVIEW200_4",
                                        "message": "리뷰 삭제가 완료되었습니다.",
                                        "result": null
                                    }
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "경로 변수 형식 오류 (scheduleId, reviewId가 숫자가 아님)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": false,
                                        "code": "COMMON400_1",
                                        "message": "잘못된 요청입니다.",
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
                    description = "다른 가족의 리뷰이거나(가족 공간 없음 포함) 본인이 작성한 리뷰가 아닌 경우",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "다른 가족의 리뷰",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "REVIEW403_2",
                                                        "message": "해당 일정의 리뷰를 조회할 권한이 없습니다.",
                                                        "result": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "같은 가족이지만 본인이 작성한 리뷰가 아님",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "REVIEW403_3",
                                                        "message": "본인이 작성한 리뷰만 수정·삭제할 수 있습니다.",
                                                        "result": null
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "리뷰나 사용자가 존재하지 않는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "리뷰 없음 (해당 일정의 리뷰가 아닌 경우 포함)",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "REVIEW404_1",
                                                        "message": "존재하지 않는 리뷰입니다.",
                                                        "result": null
                                                    }
                                                    """
                                    ),
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
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
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
    ApiResponse<Void> deleteReview(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @PathVariable Long scheduleId, @PathVariable Long reviewId);

    // 대표사진 변경
    @Operation(
            summary = "대표사진 변경 API",
            description = """
                    # 대표사진 변경
                    일정(활동)의 대표사진을 해당 일정에 등록된 리뷰 사진 중 1장으로 지정합니다.
                    대표사진은 캘린더의 날짜 썸네일과 활동 카드에 반영되며, 변경 결과는 가족 전체에 동일하게 적용됩니다.

                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                        - Content-Type: application/json
                    - **Path Variable**
                        - scheduleId: 대표사진을 변경할 일정 ID
                    - **Body**
                        - reviewPhotoId: 대표사진으로 지정할 리뷰 사진 ID (리뷰 목록 조회 응답의 photos[].reviewPhotoId)

                    ## 동작 방식
                    1. Access Token으로 인증된 사용자와 그 가족을 확인합니다.
                    2. scheduleId로 일정을 조회하고, 요청자 가족의 일정인지 검증합니다. (작성자·참여자 여부와 무관하게 가족 구성원이면 변경 가능)
                    3. reviewPhotoId가 해당 일정의 리뷰 사진인지 확인합니다.
                    4. 일정의 대표사진을 해당 사진으로 변경합니다.

                    ## 참고
                    - 대표사진을 지정하지 않은 일정은 작성순으로 가장 빠른 리뷰의 첫 번째 사진이 대표사진입니다.
                    - 대표사진이 속한 리뷰가 삭제되거나 해당 사진이 리뷰 수정으로 빠지면, 위 기본값 규칙으로 다시 정해집니다.
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
                                        "code": "REVIEW200_5",
                                        "message": "대표사진 변경이 완료되었습니다.",
                                        "result": {
                                            "scheduleId": 10,
                                            "representativePhotoId": 3
                                        }
                                    }
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 형식 오류 (reviewPhotoId 누락, scheduleId가 숫자가 아님)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": false,
                                        "code": "COMMON400_1",
                                        "message": "잘못된 요청입니다.",
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
                    description = "다른 가족의 일정이거나 가족 공간이 없는 경우",
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
                    description = "일정, 회원 또는 리뷰 사진이 존재하지 않는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "해당 일정에 없는 리뷰 사진",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "REVIEW404_2",
                                                        "message": "해당 일정에 존재하지 않는 리뷰 사진입니다.",
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
                    responseCode = "500",
                    description = "서버 내부 오류",
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
    ApiResponse<ReviewResDTO.RepresentativePhoto> changeRepresentativePhoto(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @PathVariable Long scheduleId, @Valid @RequestBody ReviewReqDTO.RepresentativePhoto dto);
}
