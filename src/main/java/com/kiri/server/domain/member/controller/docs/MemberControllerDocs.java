package com.kiri.server.domain.member.controller.docs;

import com.kiri.server.domain.member.dto.MemberReqDTO;
import com.kiri.server.domain.member.dto.MemberResDTO;
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
                    4. termsAgreed 상태를 함께 반환합니다.
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
                                        name = "위조·형식 오류 토큰 사용",
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
                                                    "code": "AUTH401_1",
                                                    "message": "유효하지 않은 토큰입니다.",
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

    // 닉네임 설정
    @Operation(
        summary = "닉네임 설정 API",
        description = """
                    # 닉네임 설정
                    로그인 회원의 닉네임을 설정하거나 변경합니다. 닉네임은 한글·영문·숫자로 구성된 2~12자의 값을 사용합니다.
                    
                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                        - Content-Type: application/json
                    - **Body**
                        - nickname: 설정할 닉네임
                    
                    ## 동작 방식
                    1. Access Token을 통해 인증된 회원을 확인합니다.
                    2. 클라이언트에서 닉네임의 앞뒤 공백을 제거한 후 요청합니다.
                    3. 닉네임의 길이가 2~12자인지 검증합니다.
                    4. 닉네임에 한글, 영문, 숫자 외의 문자가 포함되어 있는지 검증합니다.
                    5. 금칙어 및 운영자 사칭 표현이 포함되어 있는지 검증합니다.
                    6. 동일한 닉네임을 사용 중인 회원이 있는지 확인합니다.
                    7. 모든 검증을 통과하면 해당 회원의 닉네임을 설정하거나 변경합니다.
                    8. 설정된 닉네임을 반환합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공 예시",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                        "isSuccess": true,
                                        "code": "MEMBER200_1",
                                        "message": "닉네임이 설정되었습니다.",
                                        "result": {
                                            "nickname": "재현"
                                        }
                                    }
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "닉네임 입력 조건을 충족하지 않는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "닉네임이 입력되지 않은 경우",
                                            value = """
                                            {
                                                "isSuccess": false,
                                                "code": "COMMON400_1",
                                                "message": "잘못된 요청입니다.",
                                                "result": {
                                                    "nickname": "닉네임은 필수입니다."
                                                }
                                            }
                                            """
                                    ),
                                    @ExampleObject(
                                            name = "닉네임의 길이가 조건에 맞지 않는 경우",
                                            value = """
                                            {
                                                "isSuccess": false,
                                                "code": "COMMON400_1",
                                                "message": "잘못된 요청입니다.",
                                                "result": {
                                                    "nickname": "닉네임은 2~12자로 입력해주세요."
                                                }
                                            }
                                            """
                                    ),
                                    @ExampleObject(
                                            name = "공백 또는 특수문자가 포함된 경우",
                                            value = """
                                            {
                                                "isSuccess": false,
                                                "code": "COMMON400_1",
                                                "message": "잘못된 요청입니다.",
                                                "result": {
                                                    "nickname": "닉네임은 한글, 영문, 숫자만 사용할 수 있습니다."
                                                }
                                            }
                                            """
                                    )
                            })
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
                    description = "memberId에 해당하는 회원 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
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
                    responseCode = "409",
                    description = "동일한 닉네임이 이미 존재하는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "닉네임 중복",
                                            value = """
                                                {
                                                    "isSuccess": false,
                                                    "code": "MEMBER409_1",
                                                    "message": "이미 사용 중인 닉네임입니다.",
                                                    "result": null
                                                }
                                            """
                                    ),
                                    @ExampleObject(
                                            name = "동시 요청으로 인한 중복",
                                            value = """
                                                {
                                                    "isSuccess": false,
                                                    "code": "COMMON409_1",
                                                    "message": "이미 존재하는 데이터이거나 요청이 충돌합니다.",
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
        ),
    })
    ApiResponse<MemberResDTO.Nickname> updateNickname(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @Valid @RequestBody MemberReqDTO.Nickname dto);

    // 가족 역할 선택
    @Operation(
            summary = "가족 역할 선택 API",
            description = """
                    # 가족 역할 선택
                    로그인 회원의 가족 내 역할을 설정합니다. 가족 역할은 아빠·엄마·아들·딸 중 하나를 선택합니다.
                    
                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                        - Content-Type: application/json
                    - **Body**
                        - familyRole: 선택할 가족 역할
                    
                    ## 동작 방식
                    1. Access Token을 검증하여 로그인 회원의 memberId를 확인합니다.
                    2. 요청받은 familyRole이 허용된 가족 역할인지 검증합니다.
                    3. memberId로 회원을 조회합니다.
                    4. 회원의 가족 역할을 요청받은 역할로 변경합니다.
                    5. 변경된 가족 역할을 응답합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공 예시",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                        "isSuccess": true,
                                        "code": "MEMBER200_2",
                                        "message": "가족 역할이 설정되었습니다.",
                                        "result": {
                                            "familyRole": "FATHER"
                                        }
                                    }
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "가족 역할이 누락되었거나 허용되지 않은 값인 경우",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "familyRole 누락",
                                            value = """
                                            {
                                                "isSuccess": false,
                                                "code": "COMMON400_1",
                                                "message": "잘못된 요청입니다.",
                                                "result": {
                                                    "familyRole": "가족 역할은 필수입니다."
                                                }
                                            }
                                            """
                                    ),
                                    @ExampleObject(
                                            name = "유효하지 않은 familyRole",
                                            value = """
                                            {
                                                "isSuccess": false,
                                                "code": "COMMON400_1",
                                                "message": "잘못된 요청입니다.",
                                                "result": {
                                                    "familyRole": "유효하지 않은 가족 역할입니다."
                                                }
                                            }
                                            """
                                    )
                            })
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
                    description = "memberId에 해당하는 회원 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
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
            ),
    })
    ApiResponse<MemberResDTO.SelectFamilyRole> selectFamilyRole(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @Valid @RequestBody MemberReqDTO.SelectFamilyRole dto);

    // 로그아웃
    @Operation(
            summary = "로그아웃 API",
            description = """
                    # 로그아웃
                    accessToken의 memberId로 서버가 해당 refreshToken을 찾아 무효화합니다.
                    
                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                        - Content-Type: application/json
                    - **Body**
                        - nickname: 설정할 닉네임
                    
                    ## 동작 방식
                    1. Access Token으로 현재 회원을 인증합니다.
                    2. 회원 DB에 저장된 Refresh Token을 삭제합니다.
                    3. 이후 기존 Refresh Token으로는 토큰 재발급을 할 수 없습니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공 예시",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                        "isSuccess": true,
                                        "code": "AUTH200_3",
                                        "message": "로그아웃 성공",
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
                    description = "memberId에 해당하는 회원 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
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
            ),
    })
    ApiResponse<Void> logout(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId);

    // 회원 탈퇴
    @Operation(
            summary = "회원 탈퇴 API",
            description = """
                    # 회원 탈퇴
                    로그인 회원의 계정/관련 데이터를 삭제합니다.
                    
                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                        - Content-Type: application/json
                    
                    ## 동작 방식
                    1. 로그인한 회원의 탈퇴 요청을 처리합니다.
                    2. 회원 정보는 물리적으로 삭제하지 않고 탈퇴 처리(`deletedAt`)합니다.
                    3. 탈퇴한 회원의 개인정보 및 회원 종속 데이터는 삭제합니다.
                    4. 질문 답변, 리뷰, 일정 참여 기록, 버킷리스트 등 기존 서비스 이용 이력은 보존합니다.
                    5. 기존 이용 이력에서 탈퇴한 회원은 **"탈퇴한 사용자"**로 표시됩니다.
                    6. 탈퇴한 회원이 가족의 방장인 경우, 가족에 남아 있는 활성 회원 중 가장 먼저 가입한 회원에게 방장 권한을 승계합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공 예시",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                        "isSuccess": true,
                                        "code": "MEMBER200_2",
                                        "message": "회원 탈퇴가 완료되었습니다.",
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
                    description = "memberId에 해당하는 회원 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
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
            ),
    })
    ApiResponse<Void> withdraw(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId);
}
