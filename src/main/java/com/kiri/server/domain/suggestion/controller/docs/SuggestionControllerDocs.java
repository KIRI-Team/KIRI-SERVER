package com.kiri.server.domain.suggestion.controller.docs;

import com.kiri.server.domain.suggestion.dto.SuggestionReqDTO;
import com.kiri.server.domain.suggestion.dto.SuggestionResDTO;
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

@Tag(name = "건의 API")
public interface SuggestionControllerDocs {

    // 건의 작성
    @Operation(
            summary = "건의 작성 API",
            description = """
                    # 건의 작성
                    사용자가 서비스에 대한 건의를 작성합니다.
                    
                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                        - Content-Type: application/json
                    
                    ## 동작 방식
                    1. Access Token을 통해 인증된 사용자를 확인합니다.
                    2. 요청으로 전달된 건의 내용을 검증합니다.
                    3. 인증된 사용자와 건의 내용을 연결하여 건의 정보를 생성합니다.
                    4. 첨부 이미지 URL이 존재하면 함께 저장합니다.
                    5. 생성된 건의 ID를 반환합니다.
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
                                        "code": "SUGGESTION201_1",
                                        "message": "건의 작성에 성공했습니다.",
                                        "result": {
                                            "suggestionId": 1,
                                        }
                                    }
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "건의 내용 누락 또는 500자 초과",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "건의 내용 누락",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "content": "건의 내용은 필수입니다."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "건의 내용 500자 초과",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "content": "건의 내용은 최대 500자까지 입력할 수 있습니다."
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
                    description = "사용자가 존재하지 않는 경우",
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
    ApiResponse<SuggestionResDTO.Create> createSuggestion(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @Valid @RequestBody SuggestionReqDTO.Create dto);
}
