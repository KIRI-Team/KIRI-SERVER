package com.kiri.server.domain.bucket.controller.docs;

import com.kiri.server.domain.bucket.dto.BucketReqDTO;
import com.kiri.server.domain.bucket.dto.BucketResDTO;
import com.kiri.server.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
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
                                      "message": "가족공간에 소속된 회원만 이용할 수 있습니다.",
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
            @PathVariable Long missionId,
            @Parameter(description = "끼리 픽 카드에서 저장했으면 피드 응답의 kiriPick.kiriPickId. 성과 집계에 쓰입니다.")
            Long kiriPickId
    );

    @Operation(
            summary = "가족 버킷리스트 조회 API",
            description = """
                    # 가족 버킷리스트 조회

                    ## 요청 형식
                    - 헤더: Authorization: Bearer {JWT 토큰}
                    - Query: memberId, cursor, size (모두 선택)

                    로그인한 회원의 현재 가족공간 구성원들이 저장한 버킷 항목을 모아서 조회합니다.
                    SAVED·SCHEDULED·COMPLETED 항목을 모두 최신 저장순으로 반환합니다.

                    ## 조회 규칙
                    - 가족공간에 소속된 회원만 조회할 수 있습니다. 둘러보기 회원은 BUCKET403_1입니다.
                    - memberId를 넣으면 그 구성원이 저장한 항목만 반환하고, 생략하면 전체를 반환합니다.
                    - savers는 필터 칩에 쓰는 현재 가족 구성원 목록이며, 구성원별 저장 개수(bucketCount)를 담습니다.
                      저장 개수는 memberId 필터나 페이지와 상관없이 가족 전체 기준입니다.
                    - 저장한 회원이 탈퇴한 항목은 saver.withdrawn이 true이고 nickname은 '탈퇴한 사용자', familyRole은 null입니다.
                      탈퇴한 회원은 savers에 포함되지 않으며, 그 회원이 저장한 항목은 전체 조회에서 확인합니다.
                    - schedulable은 SAVED이면서 미션이 공개 중(ACTIVE)이고 기간이 끝나지 않았을 때만 true입니다.
                    - deletable은 로그인한 회원이 저장했고 SAVED일 때만 true입니다. 삭제하기 버튼 노출에 사용합니다.
                    - 미션이 종료·취소·비공개로 바뀌어도 항목은 그대로 반환하며, mission.status로 구분합니다.
                    - scheduleId·scheduledDate는 일정 기능이 연결되기 전까지 null입니다.

                    ## 페이지
                    첫 조회는 cursor 없이 요청하고, 다음 조회부터 응답의 nextCursor를 넣습니다. hasNext가 false면 마지막입니다.
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
                                      "code": "BUCKET200_1",
                                      "message": "가족 버킷리스트를 조회했습니다.",
                                      "result": {
                                        "savers": [
                                          {
                                            "memberId": 1,
                                            "nickname": "끼리엄마",
                                            "familyRole": "MOTHER",
                                            "bucketCount": 1
                                          },
                                          {
                                            "memberId": 2,
                                            "nickname": "끼리아빠",
                                            "familyRole": "FATHER",
                                            "bucketCount": 2
                                          },
                                          {
                                            "memberId": 3,
                                            "nickname": "끼리딸",
                                            "familyRole": "DAUGHTER",
                                            "bucketCount": 1
                                          }
                                        ],
                                        "bucketItems": [
                                          {
                                            "bucketItemId": 42,
                                            "status": "SAVED",
                                            "savedAt": "2026-10-27T19:40:12.345",
                                            "completedAt": null,
                                            "mission": {
                                              "missionId": 130,
                                              "title": "광장시장에서 시장음식 먹어보기",
                                              "imageUrl": "https://tong.visitkorea.or.kr/cms/resource/00/0000000_image2_1.jpg",
                                              "imageSource": "한국관광공사 (공공누리 제1유형)",
                                              "status": "ACTIVE"
                                            },
                                            "saver": {
                                              "memberId": 2,
                                              "nickname": "끼리아빠",
                                              "familyRole": "FATHER",
                                              "withdrawn": false
                                            },
                                            "schedulable": true,
                                            "deletable": true,
                                            "scheduleId": null,
                                            "scheduledDate": null
                                          },
                                          {
                                            "bucketItemId": 31,
                                            "status": "SAVED",
                                            "savedAt": "2026-10-20T10:05:00",
                                            "completedAt": null,
                                            "mission": {
                                              "missionId": 98,
                                              "title": "한강 뚜벅뚜벅 축제 방문하기",
                                              "imageUrl": null,
                                              "imageSource": null,
                                              "status": "ENDED"
                                            },
                                            "saver": {
                                              "memberId": 4,
                                              "nickname": "탈퇴한 사용자",
                                              "familyRole": null,
                                              "withdrawn": true
                                            },
                                            "schedulable": false,
                                            "deletable": false,
                                            "scheduleId": null,
                                            "scheduledDate": null
                                          }
                                        ],
                                        "nextCursor": 31,
                                        "hasNext": true
                                      }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "파라미터 형식 오류 - size 범위 밖, memberId·cursor가 숫자가 아님(COMMON400_1)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "COMMON400_1",
                                      "message": "잘못된 요청입니다.",
                                      "result": {
                                        "size": "size는 1 이상 50 이하여야 합니다."
                                      }
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
                                      "message": "가족공간에 소속된 회원만 이용할 수 있습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "토큰의 회원이 없음(MEMBER404_1)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "MEMBER404_1",
                                      "message": "존재하지 않는 회원입니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<BucketResDTO.FamilyBuckets> getFamilyBuckets(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @ParameterObject BucketReqDTO.FamilyBucketCondition condition
    );

    @Operation(
            summary = "버킷리스트 항목 삭제 API",
            description = """
                    # 버킷리스트 항목 삭제

                    ## 요청 형식
                    - 헤더: Authorization: Bearer {JWT 토큰}
                    - Path: bucketItemId
                    - Body: 없음

                    로그인한 회원이 저장한 버킷 항목을 삭제합니다. 삭제한 뒤에는 같은 미션을 다시 저장할 수 있습니다.

                    ## 삭제 규칙
                    - 없는 항목이거나 다른 가족의 항목이면 BUCKET404_1입니다.
                    - 같은 가족의 항목이라도 본인이 저장한 항목이 아니면 BUCKET403_2입니다. 저장한 회원이 탈퇴한 항목도 같습니다.
                    - 일정이 잡힌 항목(SCHEDULED)은 BUCKET409_3입니다. 일정을 취소하면 SAVED로 돌아와 삭제할 수 있습니다.
                    - 완료된 항목(COMPLETED)은 완료 기록으로 남기므로 BUCKET409_4입니다.
                    - 가족 버킷리스트 조회 응답에서 deletable이 true인 항목만 삭제할 수 있습니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "삭제 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "BUCKET200_2",
                                      "message": "버킷리스트에서 삭제했습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "bucketItemId 형식 오류(COMMON400_1)",
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
                    description = "본인이 저장한 항목이 아님(BUCKET403_2)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "BUCKET403_2",
                                      "message": "본인이 저장한 버킷 항목만 삭제할 수 있습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "토큰의 회원이 없음(MEMBER404_1) / 없거나 다른 가족의 버킷 항목(BUCKET404_1)",
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
                                            name = "버킷 항목 없음",
                                            value = """
                                                    {
                                                      "isSuccess": false,
                                                      "code": "BUCKET404_1",
                                                      "message": "존재하지 않는 버킷 항목입니다.",
                                                      "result": null
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "일정이 잡힌 항목(BUCKET409_3) / 완료된 항목(BUCKET409_4)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "일정이 잡힌 항목",
                                            value = """
                                                    {
                                                      "isSuccess": false,
                                                      "code": "BUCKET409_3",
                                                      "message": "일정이 잡힌 버킷 항목은 일정을 취소한 뒤 삭제할 수 있습니다.",
                                                      "result": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "완료된 항목",
                                            value = """
                                                    {
                                                      "isSuccess": false,
                                                      "code": "BUCKET409_4",
                                                      "message": "완료된 버킷 항목은 삭제할 수 없습니다.",
                                                      "result": null
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    })
    ApiResponse<Void> deleteBucket(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @PathVariable Long bucketItemId
    );
}
