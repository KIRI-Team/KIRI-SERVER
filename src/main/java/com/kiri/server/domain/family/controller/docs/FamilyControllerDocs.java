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
                                                            "name": "가족 이름은 2자 이상 15자 이하이며 특수문자를 사용할 수 없습니다."
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
                                        "result": {
                                            "inviteCode": "NWTKYJ"
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
                    description = "요청한 가족의 구성원이 아닌 사용자가 초대 정보를 조회하려는 경우",
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

    // 가족명 변경
    @Operation(
            summary = "가족명 변경 API",
            description = """
                    # 가족명 변경
                    가족 공간의 이름을 변경합니다.
                    
                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                        - Content-Type: application/json
                    - **Body**
                        - name: 가족명
                    - **Path Variable**
                        - familyId: 가족 ID
                    
                    ## 동작 방식
                    1. Access Token을 통해 인증된 사용자를 확인합니다.
                    2. 요청한 familyId에 해당하는 가족 공간이 존재하는지 확인합니다.
                    3. 인증된 사용자가 해당 가족 공간의 현재 owner인지 확인합니다.
                    4. 요청받은 가족명의 앞뒤 공백을 제거합니다.
                    5. 가족명 내부의 연속된 공백을 하나의 공백으로 정규화합니다.
                    6. 정규화된 가족명이 2~15자인지 확인합니다.
                    7. 정규화된 가족명이 한글, 영문, 숫자 및 띄어쓰기만 포함하는지 확인합니다.
                    8. 금칙어 및 운영자 사칭 표현이 포함되어 있는지 확인합니다.
                    9. 정규화된 가족명이 서비스 전체에서 이미 사용 중인지 확인합니다.
                    10. 모든 검증 조건을 충족한 경우 가족명을 변경합니다.
                    11. 변경된 가족명은 해당 가족 공간의 모든 구성원에게 동일하게 반영됩니다.
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
                                        "code": "FAMILY200_4",
                                        "message": "가족명이 변경되었습니다.",
                                        "result": {
                                            "familyId": 1,
                                            "name": "끼리네 가족2"
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
                                                            "name": "가족 이름은 2자 이상 15자 이하이며 특수문자를 사용할 수 없습니다."
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
                    responseCode = "403",
                    description = "요청한 가족의 구성원이 아닌 사용자가 가족명을 변경하려는 경우",
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
    ApiResponse<FamilyResDTO.UpdateName> updateFamilyName(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @PathVariable Long familyId, @Valid @RequestBody FamilyReqDTO.UpdateName dto);

    // 가족 주소 설정
    @Operation(
            summary = "가족 주소 설정 API",
            description = """
                    # 가족 주소 설정
                    가족의 주소 정보를 저장하거나 수정합니다. 우편번호, 기본 주소, 상세 주소 및 위치 정보를 전달받아 해당 가족의 주소 정보를 갱신합니다.
                    
                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                        - Content-Type: application/json
                    - **Body**
                        - postalCode: 우편번호
                        - address: 기본 주소
                        - detailAddress: 상세 주소
                        - regionCode: 법정동 코드
                        - latitude: 위도
                        - longitude: 경도
                    - **Path Variable**
                        - familyId: 가족 주소를 설정할 가족 ID
                    
                    ## 동작 방식
                    1. Access Token을 통해 인증된 사용자를 확인합니다.
                    2. 요청한 familyId에 해당하는 가족 공간이 존재하는지 확인합니다.
                    3. 인증된 사용자가 해당 가족 공간의 구성원인지 확인합니다.
                    4. 요청받은 우편번호, 기본 주소, 상세 주소, 법정동 코드, 위도 및 경도 정보의 유효성을 검증합니다.
                    5. 검증이 완료되면 해당 가족 공간의 주소 정보를 갱신합니다.
                    6. 변경된 주소 정보를 응답 DTO로 변환합니다.
                    7. 갱신된 가족 공간의 주소 정보를 반환합니다.
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
                                       "code": "FAMILY200_5",
                                       "message": "가족 주소가 저장되었습니다.",
                                       "result": {
                                     	  "familyId": 1,
                                         "postalCode": "06134",
                                         "address": "서울특별시 서초구 강남대로 143",
                                         "detailAddress": "810동 603호",
                                         "regionCode": "1165010800",
                                         "latitude": 37.5519,
                                         "longitude": 126.9254
                                       }
                                     }
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "필수 입력값 누락 또는 잘못된 주소 정보",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "postalCode 누락 또는 공백",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "postalCode": "우편번호는 필수입니다."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "postalCode가 5자리 숫자가 아닌 경우",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "postalCode": "우편번호는 5자리 숫자여야 합니다."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "address 누락 또는 공백",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "address": "기본 주소는 필수입니다."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "regionCode 누락 또는 공백",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "regionCode": "지역 코드는 필수입니다."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "latitude 누락",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "latitude": "위도는 필수입니다."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "latitude가 -90 ~ 90 범위를 벗어남",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "latitude": "위도는 -90 이상 90 이하여야 합니다."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "longitude 누락",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "longitude": "경도는 필수입니다."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "longitude가 -180 ~ 180 범위를 벗어남",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "longitude": "경도는 -180 이상 180 이하여야 합니다."
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
                    responseCode = "403",
                    description = "요청한 가족의 구성원이 아닌 사용자가 가족명을 변경하려는 경우",
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
    ApiResponse<FamilyResDTO.UpdateAddress> updateFamilyAddress(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @PathVariable Long familyId, @Valid @RequestBody FamilyReqDTO.UpdateAddress dto);

    // 가족 ID 조회
    @Operation(
            summary = "가족 ID 조회 API",
            description = """
                    # 가족 ID 조회
                    인증된 사용자가 소속된 가족의 ID를 조회합니다.
                    
                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                        - Content-Type: application/json
                    
                    ## 동작 방식
                    1. Access Token을 통해 인증된 사용자를 확인합니다.
                    2. 요청한 familyId에 해당하는 가족 공간이 존재하는지 확인합니다.
                    3. 소속된 가족 공간이 존재하지 않으면 예외를 발생시킵니다.
                    4. 조회된 가족 공간의 ID를 응답 DTO로 변환합니다.
                    5. 가족 ID를 반환합니다.
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
                                        "code": "FAMILY200_6",
                                        "message": "가족 ID 조회에 성공했습니다.",
                                        "result": {
                                          "familyId": 1
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
    ApiResponse<FamilyResDTO.Id> getFamilyId(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId);

    // 가족 초대 코드 입력
    @Operation(
            summary = "가족 초대 코드 입력 API",
            description = """
                    # 가족 초대 코드 입력
                    초대 코드를 입력하여 해당 가족에 가입합니다.
                    
                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                        - Content-Type: application/json
                    
                    ## 동작 방식
                    1. Access Token을 통해 인증된 사용자를 확인합니다.
                    2. 입력받은 초대 코드에 해당하는 가족을 조회합니다.
                    3. 초대 코드의 존재 여부와 만료 여부를 검증합니다.
                    4. 사용자가 이미 다른 가족에 가입되어 있는지 확인합니다.
                    5. 검증이 완료되면 해당 가족에 사용자를 등록합니다.
                    6. 가족 ID와 가족 이름을 반환합니다.
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
                                        "code": "FAMILY200_7",
                                        "message": "가족 가입에 성공했습니다.",
                                        "result": {
                                          "familyId": 1,
                                          "familyName": "끼리네가족"
                                        }
                                      }
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "초대 코드 형식이 올바르지 않음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "초대 코드 누락",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "inviteCode": "초대 코드는 필수입니다."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "초대 코드 규칙에 맞지 않는 경우",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "inviteCode": "초대 코드는 영문 대문자와 숫자 6자리여야 합니다."
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
                    responseCode = "403",
                    description = "요청한 가족의 구성원이 아닌 사용자가 초대 코드를 입력하려는 경우",
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
                                    ),
                                    @ExampleObject(
                                            name = "유효하지 않은 초대 코드인 경우",
                                            value = """
                                                            {
                                                                "isSuccess": false,
                                                                "code": "FAMILY404_2",
                                                                "message": "유효하지 않은 초대 코드입니다.",
                                                                "result": null
                                                            }
                                                    """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 소속된 가족이 있는 사용자인 경우",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": false,
                                        "code": "FAMILY409_2",
                                        "message": "이미 다른 가족에 가입되어 있습니다.",
                                        "result": null
                                    }
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "410",
                    description = "초대 코드 만료",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": false,
                                        "code": "FAMILY410_1",
                                        "message": "초대 코드가 만료되었습니다.",
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
    ApiResponse<FamilyResDTO.Join> joinFamily(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @Valid @RequestBody FamilyReqDTO.Join dto);

    // 가족 초대 토큰 정보 조회
    @Operation(
            summary = "가족 초대 토큰 정보 조회 API",
            description = """
                    # 가족 초대 토큰 정보 조회
                    초대 토큰의 유효성을 확인하고 초대된 가족 정보를 조회합니다.
                    
                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                        - Content-Type: application/json
                    
                    ## 동작 방식
                    1. Access Token을 통해 인증된 사용자를 확인합니다.
                    2. 전달받은 초대 토큰으로 가족 정보를 조회합니다.
                    3. 초대 토큰의 유효 여부와 만료 여부를 확인합니다.
                    4. 초대 토큰이 유효한 경우 해당 가족의 ID와 이름을 반환합니다.
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
                                        "code": "FAMILY200_8",
                                        "message": "가족 초대 정보를 조회했습니다.",
                                        "result": {
                                          "familyId": 1,
                                          "familyName": "끼리네가족"
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
                                    ),
                                    @ExampleObject(
                                            name = "유효하지 않은 초대 토큰인 경우",
                                            value = """
                                                            {
                                                                "isSuccess": false,
                                                                "code": "FAMILY404_3",
                                                                "message": "유효하지 않은 초대 토큰입니다.",
                                                                "result": null
                                                            }
                                                    """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 소속된 가족이 있는 사용자인 경우",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": false,
                                        "code": "FAMILY409_2",
                                        "message": "이미 다른 가족에 가입되어 있습니다.",
                                        "result": null
                                    }
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "410",
                    description = "초대 토큰 만료",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": false,
                                        "code": "FAMILY410_2",
                                        "message": "초대 토큰이 만료되었습니다.",
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
    ApiResponse<FamilyResDTO.InviteTokenInfo> getInviteTokenInfo(@PathVariable String inviteToken);

    // 가족 초대 토큰 입력
    @Operation(
            summary = "가족 초대 토큰 입력 API",
            description = """
                    # 가족 초대 토큰 입력
                    초대 토큰 입력하여 해당 가족에 가입합니다.
                    
                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                        - Content-Type: application/json
                    
                    ## 동작 방식
                    1. Access Token을 통해 인증된 사용자를 확인합니다.
                    2. 전달받은 초대 토큰으로 가족 정보를 조회합니다.
                    3. 초대 토큰의 유효 여부와 만료 여부를 확인합니다.
                    4. 사용자가 이미 다른 가족에 가입되어 있는지 확인합니다.
                    5. 초대 토큰이 유효하고 가입 이력이 없는 경우 해당 가족에 가입시킵니다.
                    6. 가족 ID와 가족 이름을 반환합니다.
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
                                        "code": "FAMILY200_7",
                                        "message": "가족 가입에 성공했습니다.",
                                        "result": {
                                          "familyId": 1,
                                          "familyName": "끼리네가족"
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
                    description = "요청한 가족의 구성원이 아닌 사용자가 초대 코드를 입력하려는 경우",
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
                                    ),
                                    @ExampleObject(
                                            name = "유효하지 않은 초대 토큰인 경우",
                                            value = """
                                                            {
                                                                "isSuccess": false,
                                                                "code": "FAMILY404_3",
                                                                "message": "유효하지 않은 초대 토큰입니다.",
                                                                "result": null
                                                            }
                                                    """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 소속된 가족이 있는 사용자인 경우",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": false,
                                        "code": "FAMILY409_2",
                                        "message": "이미 다른 가족에 가입되어 있습니다.",
                                        "result": null
                                    }
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "410",
                    description = "초대 코드 만료",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "isSuccess": false,
                                        "code": "FAMILY410_2",
                                        "message": "초대 토큰이 만료되었습니다.",
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
    ApiResponse<FamilyResDTO.Join> joinFamilyByToken(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @PathVariable String inviteToken);

    // 가족 정보 조회
    @Operation(
            summary = "가족 정보 조회 API",
            description = """
                    # 가족 정보 조회
                    특정 가족의 기본 정보 및 가족 구성원 정보를 조회합니다.
                    
                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                        - Content-Type: application/json
                    
                    ## 동작 방식
                    1. Access Token을 통해 인증된 사용자를 확인합니다.
                    2. Path Parameter로 전달받은 familyId를 통해 가족 정보를 조회합니다.
                    3. 요청한 사용자가 해당 가족의 구성원인지 확인합니다.
                    4. 해당 가족에 소속된 탈퇴하지 않은 구성원 목록을 조회합니다.
                    5. 가족 정보와 구성원 정보를 반환합니다
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
                                        "code": "FAMILY200_9",
                                        "message": "가족 정보 조회에 성공했습니다.",
                                        "result": {
                                            "familyId": 1,
                                            "familyName": "우리 가족",
                                            "ownerMemberId": 1,
                                            "memberCount": 3,
                                            "members": [
                                                {
                                                    "memberId": 1,
                                                    "nickname": "재현1",
                                                    "familyRole": "FATHER",
                                                    "characterImageUrl": "https://example.com/character1.png"
                                                },
                                                {
                                                    "memberId": 2,
                                                    "nickname": "재현2",
                                                    "familyRole": "MOTHER",
                                                    "characterImageUrl": "https://example.com/character2.png"
                                                },
                                                {
                                                    "memberId": 3,
                                                    "nickname": "재현3",
                                                    "familyRole": "SON",
                                                    "characterImageUrl": null
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
                    description = "요청한 가족의 구성원이 아닌 사용자가 초대 코드를 입력하려는 경우",
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
    ApiResponse<FamilyResDTO.FamilyInfo> getFamilyInfo(@AuthenticationPrincipal Long memberId, @PathVariable Long familyId);
}
