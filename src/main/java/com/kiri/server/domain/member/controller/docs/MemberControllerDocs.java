package com.kiri.server.domain.member.controller.docs;

import com.kiri.server.domain.member.dto.MemberReqDTO;
import com.kiri.server.domain.member.dto.MemberResDTO;
import com.kiri.server.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "회원 API")
public interface MemberControllerDocs {

    // 소셜 로그인
    @Operation(
            summary = "소셜 로그인 API",
            description = """
                    # 소셜 로그인
                    카카오/구글 ID Token(OIDC)와 nonce를 검증해 회원을 확인하고, 신규 회원이면 계정을 생성한 뒤 서버 토큰(access·refresh)을 발급합니다.
                    
                    ## 요청 형식
                    - **Header**
                        - Content-Type: application/json
                    - **Body**
                        - socialProvider : 소셜 로그인 제공자 ('KAKAO' / 'GOOGLE' / 'APPLE')
                        - socialProviderToken : Google 또는 Kakao에서 발급한 ID Token(OIDC)
                        - nonce : 소셜 로그인 요청 시 사용한 nonce
                    
                    ## 동작 방식
                    1. socialProviderToken의 서명, issuer, audience, expiration, nonce를 검증합니다.
                    2. socialProvider와 socialProviderToken로 기존 회원을 조회합니다.
                    3. 기존 회원이면 로그인을 진행합니다.
                    4. 회원이 없으면 신규 회원을 생성한 후 로그인합니다.
                    5. Access Token, Refresh Token, termsAgreed를 반환합니다.
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
                                        "code": "AUTH200_1",
                                        "message": "로그인 성공",
                                        "result": {
                                            "accessToken": "eyJ...",
                                            "refreshToken": "eyJ...",
                                            "isNewUser": true,
                                            "termsAgreed": false
                                        }
                                    }
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청값 누락 또는 지원하지 않는 socialProvider",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "socialProvider 누락",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "socialProvider": "socialProvider는 필수입니다."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "socialProviderToken 누락",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "socialProviderToken": "socialProviderToken은 필수입니다."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "nonce 누락",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "nonce": "nonce는 필수입니다."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "socialProvider가 KAKAO/GOOGLE/APPLE 외 값",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "AUTH400_1",
                                                        "message": "지원하지 않는 소셜 로그인 제공자입니다.",
                                                        "result": null
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "socialProviderToken 검증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": false,
                                        "code": "AUTH401_3",
                                        "message": "소셜 인증 토큰 검증에 실패했습니다.",
                                        "result": null
                                    }
                                    """
                            )
                    )
            )
    })
    ApiResponse<MemberResDTO.Login> login(@RequestBody MemberReqDTO.Login dto);

    // 토큰 재발급 API
    @Operation(
            summary = "토큰 재발급 API",
            description = """
                    # 토큰 재발급
                    refreshToken을 검증해 새 accessToken을 재발급합니다. 자동 로그인에도 사용.
                    
                    ## 요청 형식
                    - **Header**
                        - Content-Type: application/json
                    - **Body**
                        - refreshToken : 재발급에 사용할 Refresh Token
                    
                    ## 동작 방식
                    1. Refresh Token의 서명, 만료 여부 및 토큰 타입을 검증합니다.
                    2. 회원 DB에 저장된 Refresh Token과 일치하는지 검증합니다.
                    3. 검증에 성공하면 새로운 Access Token과 Refresh Token을 발급합니다.
                    4. onboardingCompleted와 termsAgreed 상태를 함께 반환합니다.
                    5. 기존 Refresh Token은 무효화되고 새 Refresh Token으로 교체됩니다.
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
                                        "code": "AUTH200_2",
                                        "message": "토큰 재발급 성공",
                                        "result": {
                                            "accessToken": "eyJ...",
                                            "refreshToken": "eyJ...",
                                            "termsAgreed": true
                                        }
                                    }
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "refreshToken 누락",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": false,
                                        "code": "COMMON400_1",
                                        "message": "잘못된 요청입니다.",
                                        "result": {
                                            "refreshToken": "refreshToken은 필수입니다."
                                        }
                                    }
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 (유효하지 않은 토큰, 만료된 토큰)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
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
                                            name = "refreshToken 만료",
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
    ApiResponse<MemberResDTO.TokenReissue> tokenReissue(@RequestBody MemberReqDTO.TokenReissue dto);
}
