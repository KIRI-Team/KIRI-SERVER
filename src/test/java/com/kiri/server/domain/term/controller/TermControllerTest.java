package com.kiri.server.domain.term.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kiri.server.domain.term.dto.TermResDTO;
import com.kiri.server.domain.term.exception.TermException;
import com.kiri.server.domain.term.exception.code.TermErrorCode;
import com.kiri.server.domain.term.service.TermService;
import com.kiri.server.global.config.SecurityConfig;
import com.kiri.server.global.security.handler.CustomAccessDeniedHandler;
import com.kiri.server.global.security.handler.CustomAuthenticationEntryPoint;
import com.kiri.server.global.security.jwt.JwtProvider;
import java.time.LocalDateTime;
import java.util.List;
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

@WebMvcTest(TermController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtProvider.class
})
@TestPropertySource(properties = "jwt.secret=" + TermControllerTest.SECRET)
class TermControllerTest {

    static final String SECRET = "test-secret-key-for-jwt-must-be-at-least-32-bytes";
    private static final Long MEMBER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private TermService termService;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        bearerToken = "Bearer " + jwtProvider.createAccessToken(MEMBER_ID);
    }

    @Test
    @DisplayName("토큰 없이 약관 목록을 조회하면 COMMON401_1로 응답한다.")
    void getTerms_noToken() throws Exception {
        mockMvc.perform(get("/api/v1/terms"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_1"));
    }

    @Test
    @DisplayName("약관 목록 조회에 성공하면 TERM200_1과 약관 목록을 반환한다.")
    void getTerms_success() throws Exception {
        TermResDTO.Info info = TermResDTO.Info.builder()
                .termId(1L)
                .title("서비스 이용약관")
                .shortDescription("서비스 이용약관 요약")
                .description("서비스 이용약관 본문")
                .isRequired(true)
                .updatedAt(LocalDateTime.of(2026, 10, 1, 10, 0))
                .build();
        given(termService.getTerms()).willReturn(List.of(info));

        mockMvc.perform(get("/api/v1/terms").header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TERM200_1"))
                .andExpect(jsonPath("$.result[0].termId").value(1))
                .andExpect(jsonPath("$.result[0].isRequired").value(true));
    }

    @Test
    @DisplayName("약관 동의에 성공하면 TERM200_2로 응답하고 토큰의 회원 ID로 동의를 처리한다.")
    void agree_success() throws Exception {
        mockMvc.perform(post("/api/v1/terms/agreements")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreements\":[{\"termId\":1,\"isAgreed\":true}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TERM200_2"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verify(termService).agree(eq(MEMBER_ID), any());
    }

    @Test
    @DisplayName("agreements가 없으면 COMMON400_1과 필드 에러 메시지로 응답한다.")
    void agree_missingAgreements() throws Exception {
        mockMvc.perform(post("/api/v1/terms/agreements")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.result.agreements").value("agreements는 필수입니다."));

        verify(termService, never()).agree(any(), any());
    }

    @Test
    @DisplayName("termId가 없으면 COMMON400_1과 항목 위치를 포함한 필드 에러 메시지로 응답한다.")
    void agree_missingTermId() throws Exception {
        mockMvc.perform(post("/api/v1/terms/agreements")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreements\":[{\"isAgreed\":true}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.result['agreements[0].termId']").value("termId는 필수입니다."));
    }

    @Test
    @DisplayName("필수 약관 미동의 예외가 발생하면 TERM400_1로 응답한다.")
    void agree_requiredNotAgreed() throws Exception {
        willThrow(new TermException(TermErrorCode.REQUIRED_NOT_AGREED))
                .given(termService)
                .agree(eq(MEMBER_ID), any());

        mockMvc.perform(post("/api/v1/terms/agreements")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreements\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TERM400_1"));
    }

    @Test
    @DisplayName("동의 여부 조회에 성공하면 TERM200_3과 약관별 동의 여부를 반환한다.")
    void getAgreementStatus_success() throws Exception {
        TermResDTO.AgreementStatus agreementStatus = TermResDTO.AgreementStatus.builder()
                .termsAgreed(true)
                .agreements(List.of(
                        TermResDTO.AgreementInfo.builder()
                                .termId(1L)
                                .isAgreed(true)
                                .build(),
                        TermResDTO.AgreementInfo.builder()
                                .termId(2L)
                                .isAgreed(false)
                                .build()))
                .build();
        given(termService.getAgreementStatus(MEMBER_ID)).willReturn(agreementStatus);

        mockMvc.perform(get("/api/v1/terms/agreements").header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TERM200_3"))
                .andExpect(jsonPath("$.result.termsAgreed").value(true))
                .andExpect(jsonPath("$.result.agreements[1].termId").value(2))
                .andExpect(jsonPath("$.result.agreements[1].isAgreed").value(false));
    }
}
