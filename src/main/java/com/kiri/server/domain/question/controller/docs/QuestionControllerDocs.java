package com.kiri.server.domain.question.controller.docs;

import com.kiri.server.domain.question.dto.QuestionResDTO;
import com.kiri.server.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "질문 API")
public interface QuestionControllerDocs {

    @Operation(
            summary = "오늘의 질문 조회 API",
            description = """
                    # 오늘의 질문 조회

                    ## 요청 형식
                    - 헤더: Authorization: Bearer {JWT 토큰}

                    홈 진입 시 가장 먼저 호출합니다.
                    가족에게 오늘 배정된 질문과 내 답변 여부를 조회합니다.
                    "오늘"은 한국 시간 자정 기준입니다.

                    ## 응답 필드
                    - hasFamily: 가족 소속 여부. false면 나머지 값은 비어 있으며 가족 생성 안내를 노출합니다.
                    - questionId: 가족에게 배정된 질문 ID. 이후 답변 관련 API의 path에 사용합니다.
                    - question: 질문 내용
                    - isAnswered: 내가 오늘 질문에 답변했는지 여부
                    - answer: 내 답변 내용 (답변 전이면 null)

                    ## 동작 방식
                    1. 인증된 회원이 존재하는지 확인합니다. 없으면 404(MEMBER404_1)를 반환합니다.
                    2. 가족에 속하지 않은 회원이면 에러 없이 hasFamily: false로 응답합니다.
                    3. 가족에게 오늘 배정된 질문이 없으면 404(QUESTION404_1)를 반환합니다.
                    4. 내 답변이 있으면 answer와 isAnswered: true를 함께 반환합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "답변 전", value = """
                                            {
                                              "isSuccess": true,
                                              "code": "QUESTION200_1",
                                              "message": "오늘의 질문을 성공적으로 조회하였습니다.",
                                              "result": {
                                                "hasFamily": true,
                                                "questionId": 12,
                                                "question": "오늘 가장 웃겼던 일은 무엇인가요?",
                                                "isAnswered": false,
                                                "answer": null
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "답변 후", value = """
                                            {
                                              "isSuccess": true,
                                              "code": "QUESTION200_1",
                                              "message": "오늘의 질문을 성공적으로 조회하였습니다.",
                                              "result": {
                                                "hasFamily": true,
                                                "questionId": 12,
                                                "question": "오늘 가장 웃겼던 일은 무엇인가요?",
                                                "isAnswered": true,
                                                "answer": "강아지가 산책하다 넘어졌어요."
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "가족 없음", value = """
                                            {
                                              "isSuccess": true,
                                              "code": "QUESTION200_1",
                                              "message": "오늘의 질문을 성공적으로 조회하였습니다.",
                                              "result": {
                                                "hasFamily": false,
                                                "questionId": null,
                                                "question": null,
                                                "isAnswered": false,
                                                "answer": null
                                              }
                                            }
                                            """)
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 - 토큰 미삽입(COMMON401_1) / 유효하지 않은 토큰(AUTH401_1) / 토큰 만료(AUTH401_2)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH401_2",
                                      "message": "토큰이 만료되었습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "오늘 배정된 질문이 없음(QUESTION404_1) / 토큰의 회원이 존재하지 않음(MEMBER404_1)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "QUESTION404_1",
                                      "message": "오늘의 질문이 존재하지 않습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<QuestionResDTO.TodayQuestion> getTodayQuestion(
            @Parameter(hidden = true) Long memberId
    );
}
