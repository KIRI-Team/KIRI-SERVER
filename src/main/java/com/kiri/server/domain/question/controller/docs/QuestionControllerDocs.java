package com.kiri.server.domain.question.controller.docs;

import com.kiri.server.domain.question.dto.QuestionReqDTO;
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
                            examples = {
                                    @ExampleObject(name = "토큰 미삽입", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "COMMON401_1",
                                              "message": "인증이 필요합니다.",
                                              "result": null
                                            }
                                            """),
                                    @ExampleObject(name = "유효하지 않은 토큰", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "AUTH401_1",
                                              "message": "유효하지 않은 토큰입니다.",
                                              "result": null
                                            }
                                            """),
                                    @ExampleObject(name = "토큰 만료", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "AUTH401_2",
                                              "message": "토큰이 만료되었습니다.",
                                              "result": null
                                            }
                                            """)
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "오늘 배정된 질문이 없음(QUESTION404_1) / 토큰의 회원이 존재하지 않음(MEMBER404_1)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "오늘의 질문 없음", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "QUESTION404_1",
                                              "message": "오늘의 질문이 존재하지 않습니다.",
                                              "result": null
                                            }
                                            """),
                                    @ExampleObject(name = "회원 없음", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "MEMBER404_1",
                                              "message": "존재하지 않는 회원입니다.",
                                              "result": null
                                            }
                                            """)
                            }
                    )
            )
    })
    ApiResponse<QuestionResDTO.TodayQuestion> getTodayQuestion(
            @Parameter(hidden = true) Long memberId
    );

    @Operation(
            summary = "내 답변 작성/수정 API",
            description = """
                    # 내 답변 작성/수정

                    ## 요청 형식
                    - 헤더: Authorization: Bearer {JWT 토큰}
                    - path: questionId (오늘의 질문 조회 API가 반환한 questionId)
                    - body: { "answer": "답변 내용" }

                    답변이 없으면 새로 작성하고, 이미 있으면 내용을 덮어씁니다.
                    같은 요청을 여러 번 보내도 결과는 같습니다.
                    질문이 배정된 당일(한국 시간 자정 기준)에만 작성/수정할 수 있습니다.

                    ## 요청 필드
                    - answer: 답변 내용 (필수, 공백만 입력 불가, 최대 500자)

                    ## 동작 방식
                    1. 요청 값을 검증합니다. 실패하면 400(COMMON400_1)을 반환합니다.
                       - answer 검증 실패: result에 { "answer": "에러 메시지" } 형태로 담깁니다.
                       - Body 형식 오류: result에 "요청 Body 형식이 잘못되었습니다."가 담깁니다.
                       - questionId 타입 오류: result는 null입니다.
                    2. 인증된 회원이 존재하는지 확인합니다. 없으면 404(MEMBER404_1)를 반환합니다.
                    3. 질문이 없거나 내 가족에게 배정된 질문이 아니면 404(QUESTION404_2)를 반환합니다.
                    4. 오늘 배정된 질문이 아니면 400(QUESTION400_1)을 반환합니다.
                    5. 내 답변이 있으면 수정하고, 없으면 새로 작성합니다.
                    6. 같은 회원의 첫 작성 요청이 동시에 들어오면 하나만 저장되고 나머지는 409(QUESTION409_1)를 반환합니다.
                       다시 요청하면 저장된 답변을 수정하므로 정상 처리됩니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "작성/수정 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "QUESTION200_2",
                                      "message": "답변을 성공적으로 작성하였습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = """
                            잘못된 요청
                            - COMMON400_1: answer 누락/공백, 500자 초과, Body 형식 오류, questionId가 숫자가 아님
                            - QUESTION400_1: 오늘 배정된 질문이 아님
                            """,
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "답변 누락/공백", description = "answer가 없거나 null, 빈 문자열, 공백만 있는 경우", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "COMMON400_1",
                                              "message": "잘못된 요청입니다.",
                                              "result": {
                                                "answer": "답변을 입력해주세요."
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "답변 500자 초과", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "COMMON400_1",
                                              "message": "잘못된 요청입니다.",
                                              "result": {
                                                "answer": "답변은 500자 이내로 작성해주세요."
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "Body 형식 오류", description = "Body가 없거나 JSON 문법이 틀린 경우", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "COMMON400_1",
                                              "message": "잘못된 요청입니다.",
                                              "result": "요청 Body 형식이 잘못되었습니다."
                                            }
                                            """),
                                    @ExampleObject(name = "questionId 타입 오류", description = "path의 questionId가 숫자가 아닌 경우 (예: /questions/abc/answers/me)", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "COMMON400_1",
                                              "message": "잘못된 요청입니다.",
                                              "result": null
                                            }
                                            """),
                                    @ExampleObject(name = "답변 기한 지남", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "QUESTION400_1",
                                              "message": "오늘의 질문에만 답변할 수 있습니다.",
                                              "result": null
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
                            examples = {
                                    @ExampleObject(name = "토큰 미삽입", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "COMMON401_1",
                                              "message": "인증이 필요합니다.",
                                              "result": null
                                            }
                                            """),
                                    @ExampleObject(name = "유효하지 않은 토큰", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "AUTH401_1",
                                              "message": "유효하지 않은 토큰입니다.",
                                              "result": null
                                            }
                                            """),
                                    @ExampleObject(name = "토큰 만료", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "AUTH401_2",
                                              "message": "토큰이 만료되었습니다.",
                                              "result": null
                                            }
                                            """)
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "질문이 없거나 내 가족의 질문이 아님(QUESTION404_2) / 토큰의 회원이 존재하지 않음(MEMBER404_1)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "질문 없음/내 가족의 질문 아님", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "QUESTION404_2",
                                              "message": "질문이 존재하지 않습니다.",
                                              "result": null
                                            }
                                            """),
                                    @ExampleObject(name = "회원 없음", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "MEMBER404_1",
                                              "message": "존재하지 않는 회원입니다.",
                                              "result": null
                                            }
                                            """)
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "첫 작성 요청이 동시에 들어와 중복 저장이 막힘(QUESTION409_1)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "QUESTION409_1",
                                      "message": "답변 요청이 동시에 처리되었습니다. 다시 시도해주세요.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<Void> writeAnswer(
            @Parameter(hidden = true) Long memberId,
            @Parameter(description = "가족에게 배정된 질문 ID", example = "12") Long questionId,
            QuestionReqDTO.WriteAnswer request
    );

    @Operation(
            summary = "가족 답변 조회 API",
            description = """
                    # 가족 답변 조회

                    ## 요청 형식
                    - 헤더: Authorization: Bearer {JWT 토큰}
                    - path: questionId (오늘의 질문 조회 API가 반환한 questionId)

                    질문 하나에 대한 가족 구성원 전체(나 포함)의 답변을 조회합니다.
                    답변하지 않은 구성원도 목록에 포함되며, isAnswered로 답변 여부를 구분합니다.
                    지난 질문도 조회할 수 있습니다.

                    ## 응답 필드
                    - question: 질문 내용
                    - answers: 가족 구성원별 답변 목록 (나를 맨 앞에 두고, 나머지는 가족 역할 순서)
                      - isMe: 나인지 여부
                      - memberId: 회원 ID (콕 찌르기 대상 지정에 사용)
                      - nickname: 닉네임
                      - isAnswered: 답변 여부
                      - answer: 답변 내용 (답변 전이면 null)

                    ## 동작 방식
                    1. 인증된 회원이 존재하는지 확인합니다. 없으면 404(MEMBER404_1)를 반환합니다.
                    2. 질문이 없거나 내 가족에게 배정된 질문이 아니면 404(QUESTION404_2)를 반환합니다.
                    3. 아직 공개되지 않은 미래 날짜의 질문이면 404(QUESTION404_2)를 반환합니다.
                    4. 가족 구성원 전체와 각자의 답변 여부를 반환합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "QUESTION200_3",
                                      "message": "가족 답변을 성공적으로 조회하였습니다.",
                                      "result": {
                                        "question": "오늘 가장 웃겼던 일은 무엇인가요?",
                                        "answers": [
                                          {
                                            "isMe": true,
                                            "memberId": 1,
                                            "nickname": "끼리",
                                            "isAnswered": true,
                                            "answer": "강아지가 산책하다 넘어졌어요."
                                          },
                                          {
                                            "isMe": false,
                                            "memberId": 2,
                                            "nickname": "엄마",
                                            "isAnswered": false,
                                            "answer": null
                                          }
                                        ]
                                      }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "questionId가 숫자가 아님(COMMON400_1)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "COMMON400_1",
                                      "message": "잘못된 요청입니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 - 토큰 미삽입(COMMON401_1) / 유효하지 않은 토큰(AUTH401_1) / 토큰 만료(AUTH401_2)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "토큰 미삽입", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "COMMON401_1",
                                              "message": "인증이 필요합니다.",
                                              "result": null
                                            }
                                            """),
                                    @ExampleObject(name = "유효하지 않은 토큰", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "AUTH401_1",
                                              "message": "유효하지 않은 토큰입니다.",
                                              "result": null
                                            }
                                            """),
                                    @ExampleObject(name = "토큰 만료", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "AUTH401_2",
                                              "message": "토큰이 만료되었습니다.",
                                              "result": null
                                            }
                                            """)
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "질문이 없거나 내 가족의 질문이 아니거나 아직 공개되지 않음(QUESTION404_2) / 토큰의 회원이 존재하지 않음(MEMBER404_1)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "질문 없음/내 가족의 질문 아님/미공개", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "QUESTION404_2",
                                              "message": "질문이 존재하지 않습니다.",
                                              "result": null
                                            }
                                            """),
                                    @ExampleObject(name = "회원 없음", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "MEMBER404_1",
                                              "message": "존재하지 않는 회원입니다.",
                                              "result": null
                                            }
                                            """)
                            }
                    )
            )
    })
    ApiResponse<QuestionResDTO.FamilyAnswers> getFamilyAnswers(
            @Parameter(hidden = true) Long memberId,
            @Parameter(description = "가족에게 배정된 질문 ID", example = "12") Long questionId
    );

    @Operation(
            summary = "가족 구성원 콕 찌르기 API",
            description = """
                    # 가족 구성원 콕 찌르기

                    ## 요청 형식
                    - 헤더: Authorization: Bearer {JWT 토큰}
                    - path: memberId (찌를 가족 구성원의 회원 ID, 가족 답변 조회 API의 answers[].memberId)

                    오늘의 질문 화면에서 가족 구성원을 콕 찌릅니다.
                    아직 답변하지 않은 구성원에게는 답변 재촉, 이미 답변한 구성원에게는 관심 표현의 의미입니다.
                    같은 구성원은 하루(한국 시간 자정 기준)에 한 번만 찌를 수 있습니다.

                    ## 동작 방식
                    1. path의 memberId가 숫자가 아니면 400(COMMON400_1)을 반환합니다.
                    2. 자기 자신을 찌르면 400(QUESTION400_2)을 반환합니다.
                    3. 인증된 회원이 존재하는지 확인합니다. 없으면 404(MEMBER404_1)를 반환합니다.
                    4. 가족에 속하지 않은 회원이면 403(QUESTION403_1)을 반환합니다.
                    5. 대상이 없는 회원이거나 내 가족 구성원이 아니면 404(QUESTION404_3)를 반환합니다.
                    6. 오늘 이미 찌른 구성원이면 409(QUESTION409_2)를 반환합니다. 같은 요청이 동시에 들어와도 하나만 저장되고 나머지는 같은 409를 반환합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "찌르기 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "QUESTION200_4",
                                      "message": "가족 구성원을 성공적으로 찔렀습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = """
                            잘못된 요청
                            - COMMON400_1: memberId가 숫자가 아님
                            - QUESTION400_2: 자기 자신을 찌름
                            """,
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "memberId 타입 오류", description = "path의 memberId가 숫자가 아닌 경우 (예: /questions/today/members/abc/pokes)", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "COMMON400_1",
                                              "message": "잘못된 요청입니다.",
                                              "result": null
                                            }
                                            """),
                                    @ExampleObject(name = "자기 자신 찌르기", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "QUESTION400_2",
                                              "message": "자기 자신은 찌를 수 없습니다.",
                                              "result": null
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
                            examples = {
                                    @ExampleObject(name = "토큰 미삽입", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "COMMON401_1",
                                              "message": "인증이 필요합니다.",
                                              "result": null
                                            }
                                            """),
                                    @ExampleObject(name = "유효하지 않은 토큰", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "AUTH401_1",
                                              "message": "유효하지 않은 토큰입니다.",
                                              "result": null
                                            }
                                            """),
                                    @ExampleObject(name = "토큰 만료", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "AUTH401_2",
                                              "message": "토큰이 만료되었습니다.",
                                              "result": null
                                            }
                                            """)
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "가족에 속하지 않은 회원(QUESTION403_1)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "QUESTION403_1",
                                      "message": "가족에 소속된 회원만 이용할 수 있습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "대상이 없거나 내 가족 구성원이 아님(QUESTION404_3) / 토큰의 회원이 존재하지 않음(MEMBER404_1)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "대상 없음/내 가족 구성원 아님", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "QUESTION404_3",
                                              "message": "찌를 수 있는 가족 구성원이 아닙니다.",
                                              "result": null
                                            }
                                            """),
                                    @ExampleObject(name = "회원 없음", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "MEMBER404_1",
                                              "message": "존재하지 않는 회원입니다.",
                                              "result": null
                                            }
                                            """)
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "오늘 이미 찌른 구성원(QUESTION409_2)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "QUESTION409_2",
                                      "message": "오늘은 이미 찌른 가족 구성원입니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<Void> poke(
            @Parameter(hidden = true) Long memberId,
            @Parameter(name = "memberId", description = "찌를 가족 구성원의 회원 ID", example = "2") Long receiverId
    );
}
