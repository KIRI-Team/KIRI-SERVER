package com.kiri.server.domain.term.controller.docs;

import com.kiri.server.domain.term.dto.TermReqDTO;
import com.kiri.server.domain.term.dto.TermResDTO;
import com.kiri.server.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "약관 API")
public interface TermControllerDocs {

    @Operation(summary = "약관 목록 조회 API", description = """
                    # 약관 목록 조회

                    ## 요청 형식
                    - 헤더: Authorization: Bearer {JWT 토큰}

                    약관 동의 화면에 노출할 약관 목록을 조회합니다.
                    isRequired가 true면 필수 약관, false면 선택 약관입니다.
                    description은 약관 전문, updatedAt은 약관 최종 수정일입니다.
                    (생성 후 수정된 적이 없으면 생성일을 반환합니다.)
                    """)
    @ApiResponses(
            value = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "200",
                        description = "조회 성공",
                        content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "TERM200_1",
                                      "message": "약관 목록 조회 성공",
                                      "result": [
                                        { "termId": 1, "title": "서비스 이용약관", "shortDescription": "서비스 이용 기준 안내", "description": "제1조(목적) 이 약관은 ...", "isRequired": true, "updatedAt": "2026-10-04T12:00:00" },
                                        { "termId": 2, "title": "개인정보 처리방침", "shortDescription": "수집 항목 및 보관 기간 안내", "description": "제1조(수집 항목) ...", "isRequired": true, "updatedAt": "2026-10-04T12:00:00" },
                                        { "termId": 3, "title": "마케팅 정보 수신 동의", "shortDescription": "이벤트·업데이트 안내", "description": "이벤트, 혜택 등 ...", "isRequired": false, "updatedAt": "2026-10-04T12:00:00" }
                                      ]
                                    }
                                    """))),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "401",
                        description = "인증 실패 - 토큰 미삽입(COMMON401_1) / 유효하지 않은 토큰(AUTH401_1) / 토큰 만료(AUTH401_2)",
                        content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH401_2",
                                      "message": "토큰이 만료되었습니다.",
                                      "result": null
                                    }
                                    """)))
            })
    ApiResponse<List<TermResDTO.Info>> getTerms();

    @Operation(summary = "약관 동의 처리 API", description = """
                    # 약관 동의 처리

                    ## 요청 형식
                    - 헤더: Authorization: Bearer {JWT 토큰}
                    - Body: agreements (약관별 동의 내역)

                    필수 약관에 모두 동의해야 저장됩니다.
                    선택 약관은 동의하지 않아도 서비스 이용이 가능합니다.
                    동의한 약관은 동의일자와 현재 약관 버전을 기록하고, 미동의 약관은 동의일자를 비워 둡니다.
                    이미 동의 내역이 있는 약관은 새로 저장하지 않고 갱신합니다.
                    (같은 버전에 다시 동의하면 최초 동의일자를 유지합니다.)
                    """)
    @ApiResponses(
            value = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "200",
                        description = "저장 성공",
                        content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "TERM200_2",
                                      "message": "약관 동의가 저장되었습니다.",
                                      "result": null
                                    }
                                    """))),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "400",
                        description = "필수 약관 미동의(TERM400_1) / termId·isAgreed 등 필수 필드 누락(COMMON400_1)",
                        content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "TERM400_1",
                                      "message": "필수 약관에 모두 동의해야 합니다.",
                                      "result": null
                                    }
                                    """))),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "401",
                        description = "인증 실패 - 토큰 미삽입(COMMON401_1) / 유효하지 않은 토큰(AUTH401_1) / 토큰 만료(AUTH401_2)",
                        content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH401_2",
                                      "message": "토큰이 만료되었습니다.",
                                      "result": null
                                    }
                                    """))),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "404",
                        description = "존재하지 않는 약관(TERM404_1) / 토큰의 회원이 존재하지 않음(MEMBER404_1)",
                        content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "TERM404_1",
                                      "message": "존재하지 않는 약관입니다.",
                                      "result": null
                                    }
                                    """))),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "409",
                        description = "동시 요청으로 같은 약관의 동의 내역이 중복 저장되려는 경우",
                        content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "COMMON409_1",
                                      "message": "이미 존재하는 데이터이거나 요청이 충돌합니다.",
                                      "result": null
                                    }
                                    """)))
            })
    ApiResponse<Void> agree(@Parameter(hidden = true) Long memberId, @Valid @RequestBody TermReqDTO.Agree request);

    @Operation(summary = "약관 동의 여부 조회 API", description = """
                    # 약관 동의 여부 조회

                    ## 요청 형식
                    - 헤더: Authorization: Bearer {JWT 토큰}

                    필수 약관 전체 동의 여부(termsAgreed)와 약관별 동의 내역(agreements)을 조회합니다.
                    앱 실행 시 약관 동의 화면 노출 여부 판단 및 약관 화면 복원에 사용합니다.

                    ## 동작 방식
                    1. 인증된 회원이 존재하는지 확인합니다. 없으면 404(MEMBER404_1)를 반환합니다.
                    2. 현재 버전에 동의하지 않은 필수 약관이 하나도 없으면 termsAgreed를 true로 반환합니다.
                    3. agreements는 전체 약관 기준으로 반환합니다.
                       동의 이력이 없거나, 미동의했거나, 개정 전 버전에 동의한 약관은 isAgreed가 false입니다.
                    """)
    @ApiResponses(
            value = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "200",
                        description = "조회 성공",
                        content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "TERM200_3",
                                      "message": "약관 동의 여부 조회 성공",
                                      "result": {
                                        "termsAgreed": true,
                                        "agreements": [
                                          { "termId": 1, "isAgreed": true },
                                          { "termId": 2, "isAgreed": true },
                                          { "termId": 3, "isAgreed": false }
                                        ]
                                      }
                                    }
                                    """))),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "401",
                        description = "인증 실패 - 토큰 미삽입(COMMON401_1) / 유효하지 않은 토큰(AUTH401_1) / 토큰 만료(AUTH401_2)",
                        content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH401_2",
                                      "message": "토큰이 만료되었습니다.",
                                      "result": null
                                    }
                                    """))),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "404",
                        description = "토큰의 회원이 존재하지 않음 (탈퇴 등)",
                        content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "MEMBER404_1",
                                      "message": "존재하지 않는 회원입니다.",
                                      "result": null
                                    }
                                    """)))
            })
    ApiResponse<TermResDTO.AgreementStatus> getAgreementStatus(@Parameter(hidden = true) Long memberId);
}
