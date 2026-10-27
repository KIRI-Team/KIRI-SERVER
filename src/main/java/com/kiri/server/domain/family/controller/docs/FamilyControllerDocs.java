package com.kiri.server.domain.family.controller.docs;

import com.kiri.server.domain.family.dto.FamilyReqDTO;
import com.kiri.server.domain.family.dto.FamilyResDTO;
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

@Tag(name = "가족 API")
public interface FamilyControllerDocs {

    // 가족 공간 생성
    @Operation(
            summary = "가족 공간 생성 API",
            description = """
                    # 가족 공간 생성
                    인증된 사용자가 새로운 가족 공간을 생성합니다.
                    가족 공간 생성 시 해당 사용자를 가족 공간의 첫 번째 구성원으로 등록하고, 가족 초대 코드 및 초대 토큰을 생성합니다.
                    
                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                        - Content-Type: application/json
                    - **Body**
                        - name: 가족명
                    
                    ## 동작 방식
                    1. Access Token을 통해 인증된 사용자를 확인합니다.
                    2. 요청한 가족명이 이미 존재하는지 확인합니다.
                    3. 중복되지 않은 경우 가족 공간을 생성하고, 인증된 사용자를 해당 가족 공간의 첫 번째 구성원이자 소유자로 등록합니다.
                    4. 가족 공간에 사용할 6자리 초대 코드와 초대 토큰을 생성합니다.
                    5. 초대 토큰의 유효 기간을 설정하고, 초대 링크를 생성합니다.
                    6. 생성된 가족 공간 정보를 저장하고, 가족명, 가족 ID, 초대 코드 및 초대 링크를 반환합니다.
                    7. 가족명 중복 또는 유효하지 않은 요청인 경우 해당 오류를 반환합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "성공 예시",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": true,
                                        "code": "FAMILY201_1",
                                        "message": "가족 공간이 생성되었습니다.",
                                        "result": {
                                            "familyId": 1,
                                            "name": "끼리네가족",
                                            "inviteCode": "NWTKYJ",
                                            "inviteUrl": "https://kiri.app/invite/...",
                                            "inviteExpiresAt": "2026-10-14T11:51:36"
                                        }
                                    }
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "가족 이름이 누락되었거나 이름 규칙에 맞지 않는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "가족 이름 누락",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "name": "가족 이름은 필수입니다."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "가족 이름 규칙에 맞지 않는 경우",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "name": "가족 이름은 2자 이상 20자 이하이며 특수문자를 사용할 수 없습니다."
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
                    responseCode = "409",
                    description = "동일한 가족 이름이 이미 존재하는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": false,
                                        "code": "FAMILY409_1",
                                        "message": "이미 존재하는 가족 이름입니다.",
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
    ApiResponse<FamilyResDTO.Create> createFamily(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @Valid @RequestBody FamilyReqDTO.Create dto);

    // 가족 초대 코드/링크 생성 및 재생성
    @Operation(
            summary = "가족 초대 코드/링크 생성 및 재생성 API",
            description = """
                    # 가족 초대 코드/링크 생성 및 재생성
                    가족 초대 코드 및 초대 링크를 생성하거나 기존 초대 정보를 새로운 정보로 재생성합니다.
                    재생성 시 기존 초대 코드와 링크는 더 이상 사용할 수 없으며, 새로운 초대 정보의 유효기간은 7일입니다.
                    
                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                        - Content-Type: application/json
                    - **Path Variable**
                        - familyId: 가족 ID
                    
                    ## 동작 방식
                    1. Access Token을 통해 인증된 사용자를 확인합니다.
                    2. 요청한 familyId에 해당하는 가족 공간이 존재하는지 확인합니다.
                    3. 인증된 사용자가 해당 가족 공간의 구성원인지 확인합니다.
                    4. 6자리의 새로운 초대 코드와 초대 토큰을 생성합니다.
                    5. 초대 토큰의 유효 기간을 생성 시점으로부터 7일로 설정합니다.
                    6. 기존 초대 정보가 존재하는 경우 기존 정보를 새로운 초대 정보로 갱신합니다.
                    7. 생성된 초대 코드, 초대 링크 및 만료 일시를 반환합니다.
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
                                        "code": "FAMILY200_1",
                                        "message": "가족 초대 정보가 생성되었습니다.",
                                        "result": {
                                            "inviteCode": "NWTKYJ",
                                            "inviteUrl": "https://kiri.app/invite/...",
                                            "inviteExpiresAt": "2026-10-14T11:51:36"
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
                    responseCode = "403",
                    description = "요청한 가족의 구성원이 아닌 사용자가 초대 정보를 재생성하려는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": true,
                                        "code": "FAMILY403_1",
                                        "message": "해당 가족에 대한 권한이 없습니다.",
                                        "result": null
                                    }
                                    """
                            )
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
            ),
    })
    ApiResponse<FamilyResDTO.Invite> regenerateInvite(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @PathVariable Long familyId);

    // 가족 초대 코드/링크 삭제
    @Operation(
            summary = "가족 초대 코드/링크 삭제 API",
            description = """
                    # 가족 초대 코드/링크 삭제
                    현재 발급된 가족 초대 코드 및 초대 링크를 삭제합니다.
                    초대 정보가 삭제되면 기존 초대 코드와 링크를 더 이상 사용할 수 없습니다.
                    
                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                        - Content-Type: application/json
                    - **Path Variable**
                        - familyId: 가족 ID
                    
                    ## 동작 방식
                    1. Access Token을 통해 인증된 사용자를 확인합니다.
                    2. 요청한 familyId에 해당하는 가족 공간이 존재하는지 확인합니다.
                    3. 인증된 사용자가 해당 가족 공간의 구성원인지 확인합니다.
                    4. 해당 가족 공간에 저장된 초대 코드, 초대 토큰 및 만료 일시를 삭제합니다.
                    5. 삭제된 초대 정보는 더 이상 가족 가입에 사용할 수 없습니다.
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
                                        "code": "FAMILY200_2",
                                        "message": "가족 초대 정보가 삭제되었습니다.",
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
                    responseCode = "403",
                    description = "요청한 가족의 구성원이 아닌 사용자가 초대 정보를 재생성하려는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": true,
                                        "code": "FAMILY403_1",
                                        "message": "해당 가족에 대한 권한이 없습니다.",
                                        "result": null
                                    }
                                    """
                            )
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
            ),
    })
    ApiResponse<Void> deleteInvite(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @PathVariable Long familyId);

    // 가족 초대 코드/링크 조회
    @Operation(
            summary = "가족 초대 코드/링크 조회 API",
            description = """
                    # 가족 초대 코드/링크 조회
                    가족 구성원이 현재 가족의 초대 코드 및 초대 링크 정보를 조회합니다.
                    초대 정보가 존재하지 않는 경우 초대 정보가 생성되지 않은 상태로 처리합니다.
                    
                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                        - Content-Type: application/json
                    - **Path Variable**
                        - familyId: 가족 ID
                    
                    ## 동작 방식
                    1. Access Token을 통해 인증된 사용자를 확인합니다.
                    2. 요청한 familyId에 해당하는 가족 공간이 존재하는지 확인합니다.
                    3. 인증된 사용자가 해당 가족 공간의 구성원인지 확인합니다.
                    4. 해당 가족 공간에 저장된 초대 코드, 초대 토큰 및 만료 일시를 조회합니다.
                    5. 조회된 초대 토큰을 기반으로 초대 링크를 생성합니다.
                    6. 초대 정보가 존재하지 않는 경우 초대 정보가 생성되지 않은 상태로 처리합니다.
                    7. 조회된 초대 코드, 초대 링크 및 만료 일시를 반환합니다.
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
                                        "code": "FAMILY200_2",
                                        "message": "가족 초대 정보가 삭제되었습니다.",
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
                    responseCode = "403",
                    description = "요청한 가족의 구성원이 아닌 사용자가 초대 정보를 재생성하려는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": true,
                                        "code": "FAMILY403_1",
                                        "message": "해당 가족에 대한 권한이 없습니다.",
                                        "result": null
                                    }
                                    """
                            )
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
            ),
    })
    ApiResponse<FamilyResDTO.Invite> getInvite(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @PathVariable Long familyId);
}
