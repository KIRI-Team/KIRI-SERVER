package com.kiri.server.domain.mission.controller.docs;

import com.kiri.server.domain.mission.dto.MissionReqDTO;
import com.kiri.server.domain.mission.dto.MissionResDTO;
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

@Tag(name = "미션 API")
public interface MissionControllerDocs {

    @Operation(
            summary = "미션 피드 조회 API",
            description = """
                    # 미션 피드 조회

                    ## 요청 형식
                    - 헤더: Authorization: Bearer {JWT 토큰}
                    - Query: 모든 필터는 선택이며 함께 사용할 수 있습니다.

                    미션 탭 피드를 신규순으로 조회합니다. 공개 중(ACTIVE)이고 기간이 끝나지 않은 미션만 반환합니다.

                    ## 필터
                    - category: 6개 카테고리 중 하나
                    - regionCode: 2자리면 시·도, 5자리면 시·군·구 기준
                    - distance: 기준 위치로부터 km 이내
                    - 기준 위치: latitude·longitude가 있으면 그 좌표, 없으면 가족공간의 우리 집 위치입니다.
                      둘 다 없으면(가족이 없거나 우리 집 위치 미등록) 거리 필터와 distance 값 없이 조회합니다.
                    - date: 기간형은 기간 안일 때만, 상시 장소형은 항상 포함
                    - time: 그 시각에 운영 중인 미션. 미션 운영시간이 없으면 장소 운영시간 기준
                    - isFree / maxPrice: 무료만 또는 최대 가격 이하. isFree가 true면 maxPrice는 무시합니다.
                    - 가격·운영시간 정보가 없는 미션은 필터에서 제외하지 않습니다.

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
                                      "code": "MISSION200_1",
                                      "message": "미션 피드 조회 성공",
                                      "result": {
                                        "missions": [
                                          {
                                            "missionId": 130,
                                            "title": "국립현대미술관 서울 관람하기",
                                            "imageUrl": "https://tong.visitkorea.or.kr/cms/resource/00/0000000_image2_1.jpg",
                                            "imageSource": "한국관광공사 (공공누리 제1유형)",
                                            "category": "EXHIBITION",
                                            "missionType": "PERMANENT",
                                            "placeName": "국립현대미술관 서울",
                                            "startDate": null,
                                            "endDate": null,
                                            "price": 0,
                                            "distance": 2.4
                                          }
                                        ],
                                        "nextCursor": 130,
                                        "hasNext": true
                                      }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "파라미터 형식 오류(COMMON400_1) / 거리 값 오류(MISSION400_1) / 위도·경도 중 하나만 입력(MISSION400_2) / 지역 코드 형식 오류(MISSION400_3)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "파라미터 형식 오류",
                                            value = """
                                                    {
                                                      "isSuccess": false,
                                                      "code": "COMMON400_1",
                                                      "message": "잘못된 요청입니다.",
                                                      "result": {
                                                        "size": "size는 1 이상 50 이하여야 합니다."
                                                      }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "거리 값 오류",
                                            value = """
                                                    {
                                                      "isSuccess": false,
                                                      "code": "MISSION400_1",
                                                      "message": "거리는 1, 3, 5, 10km 중 하나여야 합니다.",
                                                      "result": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "위도·경도 중 하나만 입력",
                                            value = """
                                                    {
                                                      "isSuccess": false,
                                                      "code": "MISSION400_2",
                                                      "message": "위도와 경도는 함께 입력해야 합니다.",
                                                      "result": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "지역 코드 형식 오류",
                                            value = """
                                                    {
                                                      "isSuccess": false,
                                                      "code": "MISSION400_3",
                                                      "message": "지역 코드는 2자리 또는 5자리여야 합니다.",
                                                      "result": null
                                                    }
                                                    """
                                    )
                            }
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
                    responseCode = "404",
                    description = "토큰의 회원이 존재하지 않음(MEMBER404_1)",
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
    ApiResponse<MissionResDTO.Feed> getFeed(
            @Parameter(hidden = true) Long memberId,
            @Valid @ParameterObject MissionReqDTO.FeedCondition condition
    );

    @Operation(
            summary = "미션 상세 조회 API",
            description = """
                    # 미션 상세 조회

                    ## 요청 형식
                    - 헤더: Authorization: Bearer {JWT 토큰}
                    - Path: missionId

                    미션과 장소, 필수 활동(준비물·단계), 공식 출처를 조회합니다.
                    공개 중(ACTIVE)이거나 기간이 끝난(ENDED) 미션만 조회할 수 있고, 나머지 상태는 404를 반환합니다.

                    ## 값 규칙
                    - imageUrl이 null이면 기본 이미지를 사용합니다. imageSource는 이미지가 있을 때만 내려갑니다.
                    - price는 0이면 무료, null이면 정보 없음입니다.
                    - openingTime·closingTime은 미션 값이 없으면 장소 운영시간을 사용하고, 둘 다 없으면 null입니다.
                    - durationMinMinutes·durationMaxMinutes는 예상 활동시간을 분 단위 범위로 내려줍니다.
                    - walkingLevel은 예상 걷기량으로 LOW(적음)·MEDIUM(보통)·HIGH(많음)이며, 판단할 정보가 없으면 null입니다.
                    - bucketState는 로그인한 회원 본인의 버킷 저장 상태입니다. NONE(저장 이력 없음, "버킷에 저장"), SAVED(SAVED·SCHEDULED 버킷 있음, "저장됨"), COMPLETED_ONLY(완료된 버킷만 있음, "다시 버킷에 저장")입니다.
                    - bucketSaved는 bucketState가 SAVED일 때만 true입니다. 가족이 없는 회원은 항상 NONE·false입니다.
                    - averageRating은 이 활동에 별점을 남긴 전체 사용자의 평균(소수 첫째 자리)이고, reviewCount는 그 별점 수입니다. 별점이 없으면 averageRating은 null, reviewCount는 0입니다.
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
                                      "code": "MISSION200_2",
                                      "message": "미션 상세 조회 성공",
                                      "result": {
                                        "missionId": 130,
                                        "title": "국립현대미술관 서울 관람하기",
                                        "description": "한국 근현대 미술 작품을 상설·기획 전시로 만날 수 있는 미술관입니다.",
                                        "category": "EXHIBITION",
                                        "missionType": "PERMANENT",
                                        "status": "ACTIVE",
                                        "indoorOutdoor": "INDOOR",
                                        "imageUrl": "https://tong.visitkorea.or.kr/cms/resource/00/0000000_image2_1.jpg",
                                        "imageSource": "한국관광공사 (공공누리 제1유형)",
                                        "startDate": null,
                                        "endDate": null,
                                        "openingTime": "10:00",
                                        "closingTime": "18:00",
                                        "price": 0,
                                        "durationMinMinutes": 60,
                                        "durationMaxMinutes": 120,
                                        "walkingLevel": "MEDIUM",
                                        "sourceUrl": "https://korean.visitkorea.or.kr/detail/ms_detail.do?cotid=...",
                                        "place": {
                                          "placeId": 52,
                                          "name": "국립현대미술관 서울",
                                          "address": "서울특별시 종로구 삼청로 30",
                                          "latitude": 37.5786,
                                          "longitude": 126.98
                                        },
                                        "prepareItems": [
                                          { "title": "사전 예약", "description": "일부 전시 예약 필요" }
                                        ],
                                        "steps": [
                                          { "title": "상설 전시 관람하기", "description": "마음에 드는 작품 고르기" }
                                        ],
                                        "bucketSaved": false,
                                        "bucketState": "NONE",
                                        "averageRating": 4.5,
                                        "reviewCount": 12
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
                                                      "message": "토큰이 만료되었습니다.",
                                                      "result": null
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "없는 미션이거나 노출할 수 없는 상태(MISSION404_1)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "MISSION404_1",
                                      "message": "존재하지 않는 미션입니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<MissionResDTO.Detail> getDetail(
            @Parameter(hidden = true) Long memberId,
            @Parameter(description = "미션 ID", example = "130") Long missionId
    );

    @Operation(
            summary = "주간 미션 조회 API",
            description = """
                    # 주간 미션 조회
                    가족 구성원 전체의 취향을 반영하여 미리 계산하고 저장한 추천 미션을 조회합니다.

                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                        - Content-Type: application/json

                    ## 동작 방식
                    1. Access Token을 검증하여 로그인한 회원의 memberId를 확인합니다.
                    2. memberId를 통해 탈퇴하지 않은 회원을 조회합니다.
                    3. 조회한 회원이 속한 가족 정보를 확인합니다.
                    4. 해당 가족의 FamilyMissionRecommendation 목록을 추천 순위(recommendationRank) 오름차순으로 조회합니다.
                    5. 각 추천 정보와 연결된 Mission 정보를 가져옵니다.
                    6. 조회한 정보를 MissionResDTO.Recommendation으로 변환합니다.
                    7. 추천 미션을 응답으로 내보냅니다.
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
                                      "code": "MISSION200_3",
                                      "message": "주간 미션 조회 성공",
                                      "result": {
                                        "missionId": 1,
                                        "title": "가족과 함께 산책하기",
                                        "description": "가족과 함께 가까운 공원을 산책해 보세요.",
                                        "category": "OUTDOOR",
                                        "imageUrl": "https://example.com/mission1.png",
                                        "recommendationReason": "가족 구성원들의 야외 활동 선호도가 높아요.",
                                        "score": 0.9234567890
                                      }
                                    }
                                    """)
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
                    description = "사용자나 가족이 존재하지 않는 경우",
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
                                            name = "요청한 familyId에 해당하는 가족이 존재하지 않는 경우",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "FAMILY404_1",
                                                        "message": "존재하지 않는 가족입니다.",
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
    ApiResponse<MissionResDTO.Recommendation> getRecommendations(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId);
}
