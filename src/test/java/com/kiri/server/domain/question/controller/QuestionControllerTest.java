package com.kiri.server.domain.question.controller;

import com.kiri.server.domain.question.dto.QuestionReqDTO;
import com.kiri.server.domain.question.dto.QuestionResDTO;
import com.kiri.server.domain.question.exception.QuestionException;
import com.kiri.server.domain.question.exception.code.QuestionErrorCode;
import com.kiri.server.domain.question.service.QuestionService;
import com.kiri.server.global.config.SecurityConfig;
import com.kiri.server.global.security.handler.CustomAccessDeniedHandler;
import com.kiri.server.global.security.handler.CustomAuthenticationEntryPoint;
import com.kiri.server.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QuestionController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtProvider.class})
@TestPropertySource(properties = "jwt.secret=" + QuestionControllerTest.SECRET)
class QuestionControllerTest {

    static final String SECRET = "test-secret-key-for-jwt-must-be-at-least-32-bytes";
    private static final Long MEMBER_ID = 1L;
    private static final Long QUESTION_ID = 10L;
    private static final String WRITE_ANSWER_URL = "/api/v1/questions/{questionId}/answers/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private QuestionService questionService;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        bearerToken = "Bearer " + jwtProvider.createAccessToken(MEMBER_ID);
    }

    @Test
    @DisplayName("토큰 없이 오늘의 질문을 조회하면 COMMON401_1로 응답한다.")
    void getTodayQuestion_noToken() throws Exception {
        mockMvc.perform(get("/api/v1/questions/today"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_1"));
    }

    @Test
    @DisplayName("오늘의 질문 조회에 성공하면 QUESTION200_1과 질문 정보를 반환한다.")
    void getTodayQuestion_success() throws Exception {
        QuestionResDTO.TodayQuestion todayQuestion = QuestionResDTO.TodayQuestion.builder()
                .hasFamily(true)
                .questionId(10L)
                .question("오늘 가장 웃겼던 일은?")
                .isAnswered(true)
                .answer("강아지가 산책하다 넘어졌어요.")
                .build();
        given(questionService.getTodayQuestion(MEMBER_ID)).willReturn(todayQuestion);

        mockMvc.perform(get("/api/v1/questions/today")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QUESTION200_1"))
                .andExpect(jsonPath("$.result.hasFamily").value(true))
                .andExpect(jsonPath("$.result.questionId").value(10))
                .andExpect(jsonPath("$.result.question").value("오늘 가장 웃겼던 일은?"))
                .andExpect(jsonPath("$.result.isAnswered").value(true))
                .andExpect(jsonPath("$.result.answer").value("강아지가 산책하다 넘어졌어요."));
    }

    @Test
    @DisplayName("가족이 없는 회원이면 200과 hasFamily false로 응답한다.")
    void getTodayQuestion_noFamily() throws Exception {
        QuestionResDTO.TodayQuestion noFamily = QuestionResDTO.TodayQuestion.builder()
                .hasFamily(false)
                .isAnswered(false)
                .build();
        given(questionService.getTodayQuestion(MEMBER_ID)).willReturn(noFamily);

        mockMvc.perform(get("/api/v1/questions/today")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QUESTION200_1"))
                .andExpect(jsonPath("$.result.hasFamily").value(false))
                .andExpect(jsonPath("$.result.questionId").isEmpty());
    }

    @Test
    @DisplayName("오늘 배정된 질문이 없으면 404와 QUESTION404_1로 응답한다.")
    void getTodayQuestion_todayQuestionNotFound() throws Exception {
        given(questionService.getTodayQuestion(MEMBER_ID))
                .willThrow(new QuestionException(QuestionErrorCode.TODAY_QUESTION_NOT_FOUND));

        mockMvc.perform(get("/api/v1/questions/today")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION404_1"));
    }

    @Test
    @DisplayName("답변 작성에 성공하면 QUESTION200_2로 응답하고 토큰의 회원 ID와 path의 질문 ID로 처리한다.")
    void writeAnswer_success() throws Exception {
        mockMvc.perform(put(WRITE_ANSWER_URL, QUESTION_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"강아지가 산책하다 넘어졌어요.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QUESTION200_2"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verify(questionService).writeAnswer(
                MEMBER_ID, QUESTION_ID, new QuestionReqDTO.WriteAnswer("강아지가 산책하다 넘어졌어요."));
    }

    @Test
    @DisplayName("답변이 공백이면 COMMON400_1과 필드 에러 메시지로 응답하고 서비스를 호출하지 않는다.")
    void writeAnswer_blank() throws Exception {
        mockMvc.perform(put(WRITE_ANSWER_URL, QUESTION_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.result.answer").value("답변을 입력해주세요."));

        verify(questionService, never()).writeAnswer(any(), any(), any());
    }

    @Test
    @DisplayName("답변이 500자를 넘으면 COMMON400_1과 필드 에러 메시지로 응답한다.")
    void writeAnswer_tooLong() throws Exception {
        String tooLong = "가".repeat(501);

        mockMvc.perform(put(WRITE_ANSWER_URL, QUESTION_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.result.answer").value("답변은 500자 이내로 작성해주세요."));
    }

    @Test
    @DisplayName("답변이 정확히 500자면 검증을 통과한다.")
    void writeAnswer_maxLength() throws Exception {
        mockMvc.perform(put(WRITE_ANSWER_URL, QUESTION_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"" + "가".repeat(500) + "\"}"))
                .andExpect(status().isOk());
    }
}
