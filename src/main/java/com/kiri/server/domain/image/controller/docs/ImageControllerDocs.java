package com.kiri.server.domain.image.controller.docs;

import com.kiri.server.domain.image.dto.ImageReqDTO;
import com.kiri.server.domain.image.dto.ImageResDTO;
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

@Tag(name = "이미지 API")
public interface ImageControllerDocs {

    @Operation(
            summary = "이미지 업로드용 Presigned URL 발급 API",
            description = """
                    # 이미지 업로드용 Presigned URL 발급
                    클라이언트가 S3에 이미지를 직접 업로드할 수 있는 Presigned URL을 발급합니다.

                    ## 요청 형식
                    - **Header**
                        - Authorization: Bearer {Access_Token}
                        - Content-Type: application/json
                    - **Body**
                        - contentType: 이미지 MIME 타입 (image/jpeg, image/png, image/webp)
                        - contentLength: 업로드할 파일의 바이트 수 (최대 10MB)

                    ## 동작 방식
                    1. Access Token을 통해 인증된 사용자를 확인합니다.
                    2. contentType과 contentLength(최대 10MB)를 검증합니다.
                    3. 업로드 경로를 만들고, 5분간 유효한 PUT용 Presigned URL을 반환합니다.

                    ## 업로드 방법
                    - presignedUrl로 이미지 파일을 **PUT** 요청합니다.
                    - Content-Type 헤더는 발급 요청에 보낸 contentType과 **같은 문자열**로 보내야 합니다.
                    - 파일 크기는 발급 요청의 contentLength와 정확히 같아야 합니다. 압축·리사이징은 발급 요청 전에 끝내주세요.
                    - Authorization 헤더는 보내지 않습니다.
                    - 헤더나 크기가 다르거나 URL이 만료되면 S3가 403을 반환합니다.
                    - 업로드 후 imageKey를 이미지를 사용하는 API(예: 리뷰 작성)에 전달하면 업로드가 확정됩니다.
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
                                        "code": "IMAGE200_1",
                                        "message": "Presigned URL 발급 성공",
                                        "result": {
                                            "presignedUrl": "https://kiri-dev-images-123456789012.s3.ap-northeast-2.amazonaws.com/pending/user/1/3f2b8c1e-7d4a-4f6b-9a2e-1c5d8e9f0a7b.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&...",
                                            "imageKey": "pending/user/1/3f2b8c1e-7d4a-4f6b-9a2e-1c5d8e9f0a7b.jpg",
                                            "expiresAt": "2026-10-29T14:35:00"
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
                                            name = "contentType 누락",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "contentType": "contentType은 필수입니다."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "contentLength 0 이하",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON400_1",
                                                        "message": "잘못된 요청입니다.",
                                                        "result": {
                                                            "contentLength": "contentLength는 1 이상이어야 합니다."
                                                        }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "지원하지 않는 이미지 형식",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "IMAGE400_1",
                                                        "message": "지원하지 않는 이미지 형식입니다.",
                                                        "result": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "10MB 초과",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "IMAGE400_3",
                                                        "message": "이미지 용량이 너무 큽니다.",
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
                    responseCode = "500",
                    description = "서버 오류",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "이미지 저장소 오류",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "IMAGE500_1",
                                                        "message": "이미지 저장소 처리 중 오류가 발생했습니다.",
                                                        "result": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "서버 내부 오류",
                                            value = """
                                                    {
                                                        "isSuccess": false,
                                                        "code": "COMMON500_1",
                                                        "message": "서버 내부 오류가 발생했습니다.",
                                                        "result": null
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    })
    ApiResponse<ImageResDTO.PresignedUrl> issuePresignedUrl(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @Valid @RequestBody ImageReqDTO.PresignedUrl dto);
}