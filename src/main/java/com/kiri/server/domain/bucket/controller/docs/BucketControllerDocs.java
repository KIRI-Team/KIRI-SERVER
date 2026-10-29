package com.kiri.server.domain.bucket.controller.docs;

import com.kiri.server.domain.bucket.dto.BucketResDTO;
import com.kiri.server.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "버킷리스트 API")
public interface BucketControllerDocs {

    @Operation(
            summary = "버킷리스트 저장 API",
            description = """
                    # 버킷리스트 저장

                    ## 요청 형식
                    - 헤더: Authorization: Bearer {JWT 토큰}
                    - Path: missionId
                    - Body: 없음

                    로그인한 회원 본인의 버킷리스트에 미션을 저장합니다. 버킷 항목은 회원별로 저장되고,
                    같은 가족 구성원이 같은 미션을 각각 저장할 수 있습니다.

                    ## 저장 규칙
                    - 가족공간에 소속된 회원만 저장할 수 있습니다. 둘러보기 회원은 BUCKET403_1입니다.
                    - 공개 중(ACTIVE)이고 기간이 끝나지 않은 미션만 저장할 수 있습니다.
                      기간이 끝났거나 종료(ENDED)된 미션은 BUCKET409_2이고, 조회할 수 없는 상태의 미션은 MISSION404_1입니다.
                    - 같은 회원이 같은 미션을 SAVED·SCHEDULED 상태로 이미 저장했다면 BUCKET409_1입니다.
                      완료(COMPLETED)된 뒤에는 같은 미션을 다시 저장할 수 있습니다.
                    - 저장 직후 status는 항상 SAVED입니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "저장 성공 (응답 본문의 code는 BUCKET201_1)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "BUCKET201_1",
                                      "message": "버킷리스트에 저장했습니다.",
                                      "result": {
                                        "bucketItemId": 15,
                                        "missionId": 130,
                                        "status": "SAVED",
                                        "savedAt": "2026-10-27T19:40:12.345"
                                      }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "missionId 형식 오류(COMMON400_1)",
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
                    description = "가족공간에 소속되지 않은 회원(BUCKET403_1)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "BUCKET403_1",
                                      "message": "가족공간에 소속된 회원만 저장할 수 있습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "토큰의 회원이 없음(MEMBER404_1) / 없거나 조회할 수 없는 미션(MISSION404_1)",
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
                                            name = "미션 없음",
                                            value = """
                                                    {
                                                      "isSuccess": false,
                                                      "code": "MISSION404_1",
                                                      "message": "존재하지 않는 미션입니다.",
                                                      "result": null
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 저장한 미션(BUCKET409_1) / 종료되어 저장할 수 없는 미션(BUCKET409_2)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "이미 저장함",
                                            value = """
                                                    {
                                                      "isSuccess": false,
                                                      "code": "BUCKET409_1",
                                                      "message": "이미 버킷리스트에 저장한 미션입니다.",
                                                      "result": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "저장할 수 없는 미션",
                                            value = """
                                                    {
                                                      "isSuccess": false,
                                                      "code": "BUCKET409_2",
                                                      "message": "종료되어 저장할 수 없는 미션입니다.",
                                                      "result": null
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    })
    ApiResponse<BucketResDTO.Saved> saveBucket(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @PathVariable Long missionId
    );
}
