package com.kiri.server.domain.taste.controller.docs;

import com.kiri.server.domain.taste.dto.TasteReqDTO;
import com.kiri.server.domain.taste.dto.TasteResDTO;
import com.kiri.server.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "내 취향 설정 API")
public interface TasteControllerDocs {

    @Operation(
            summary = "내 취향 조회 API",
            description = """
                    # 내 취향 조회
                    마이페이지 내 취향 설정 화면에 보여줄 내 취향을 조회합니다.

                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}

                    ## 동작 방식
                    1. Access Token을 통해 인증된 사용자를 확인합니다.
                    2. 화면 제목에 쓸 닉네임과 저장된 취향(선호 활동·공간·활동량·비용·활동시간)을 반환합니다.
                    3. 아직 취향을 설정하지 않았으면 isSet=false, categories는 빈 배열, 나머지 값은 null로 반환합니다.
                    4. 설정한 경우 공간·비용·활동시간이 null이면 '상관없음'을 선택한 것입니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공 예시",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "취향 설정함",
                                            value = """
                                                    {
                                                        "isSuccess": true,
                                                        "code": "TASTE200_1",
                                                        "message": "내 취향 조회 성공",
                                                        "result": {
                                                            "nickname": "쥬123",
                                                            "isSet": true,
                                                            "categories": ["EXHIBITION", "FOOD_CAFE"],
                                                            "indoorOutdoor": "INDOOR",
                                                            "activityLevel": "LIGHT",
                                                            "priceLevel": "FREE",
                                                            "durationLevel": "WITHIN_1H"
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "취향 설정 전",
                                            value = """
                                                    {
                                                        "isSuccess": true,
                                                        "code": "TASTE200_1",
                                                        "message": "내 취향 조회 성공",
                                                        "result": {
                                                            "nickname": "쥬123",
                                                            "isSet": false,
                                                            "categories": [],
                                                            "indoorOutdoor": null,
                                                            "activityLevel": null,
                                                            "priceLevel": null,
                                                            "durationLevel": null
                                                        }
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
                    responseCode = "404",
                    description = "memberId에 해당하는 회원 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": false,
                                        "code": "MEMBER404_1",
                                        "message": "존재하지 않는 회원입니다.",
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
    ApiResponse<TasteResDTO.MyTaste> getMyTaste(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId);

    @Operation(
            summary = "내 취향 저장 API",
            description = """
                    # 내 취향 저장
                    내 취향 5개 항목을 한 번에 저장합니다. 기존 값은 요청 값으로 모두 덮어씁니다.

                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                        - Content-Type: application/json
                    - **Body**
                        - categories: 선호 활동 1~3개 (EXHIBITION, POPUP, PERFORMANCE, FOOD_CAFE, OUTDOOR, EXPERIENCE)
                        - indoorOutdoor: 공간 (INDOOR, OUTDOOR / null이면 상관없음)
                        - activityLevel: 활동량 (LIGHT, MODERATE, ACTIVE / 필수)
                        - priceLevel: 비용 (FREE, LOW / null이면 상관없음)
                        - durationLevel: 활동시간 (WITHIN_1H, H1_TO_3, HALF_DAY_PLUS / null이면 상관없음)

                    ## 동작 방식
                    1. Access Token을 통해 인증된 사용자를 확인합니다.
                    2. 선호 활동 개수·중복, 활동량 누락, 공간 값(BOTH 불가)을 검증합니다.
                    3. 처음 저장하면 새로 만들고, 이미 있으면 요청 값으로 덮어씁니다. 저장에 실패하면 기존 값이 유지됩니다.
                    4. 저장한 값은 다음 추천부터 반영되며, 이미 배정된 이번 주 미션은 바뀌지 않습니다.
                    5. 저장된 내 취향을 조회 API와 같은 형식으로 반환합니다.
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
                                        "code": "TASTE200_2",
                                        "message": "내 취향 저장 성공",
                                        "result": {
                                            "nickname": "쥬123",
                                            "isSet": true,
                                            "categories": ["EXHIBITION", "FOOD_CAFE"],
                                            "indoorOutdoor": "INDOOR",
                                            "activityLevel": "LIGHT",
                                            "priceLevel": "FREE",
                                            "durationLevel": "WITHIN_1H"
                                        }
                                    }
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 값이 규칙에 맞지 않는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "선호 활동 개수 오류",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "categories": "선호 활동은 1~3개 선택해야 합니다."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "활동량 누락",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "activityLevel": "활동량은 필수입니다."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "없는 값 입력",
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
                                            name = "공간에 BOTH 입력",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "TASTE400_1",
                                                        "message": "공간은 INDOOR, OUTDOOR 중 하나여야 합니다.",
                                                        "result": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "선호 활동 중복",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "TASTE400_2",
                                                        "message": "선호 활동은 중복해서 선택할 수 없습니다.",
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
                    responseCode = "404",
                    description = "memberId에 해당하는 회원 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": false,
                                        "code": "MEMBER404_1",
                                        "message": "존재하지 않는 회원입니다.",
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
    ApiResponse<TasteResDTO.MyTaste> saveMyTaste(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @Valid @RequestBody TasteReqDTO.Save dto);
}
