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
                    카카오/구글 ID Token(OIDC)을 검증해 회원을 확인하고, 신규 회원이면 계정을 생성한 뒤 서버 토큰(access·refresh)을 발급합니다.
                    
                    ## 요청 형식
                    - **Header**
                        - Content-Type: application/json
                    - **Body**
                        - socialProvider : 소셜 로그인 제공자 ('KAKAO' / 'GOOGLE' / 'APPLE')
                        - socialProviderToken : Google 또는 Kakao에서 발급한 ID Token(OIDC)
                    
                    ## 동작 방식
                    1. socialProviderToken을 검증합니다.
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
}
