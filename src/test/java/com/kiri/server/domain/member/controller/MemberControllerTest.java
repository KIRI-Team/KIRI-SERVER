package com.kiri.server.domain.member.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiri.server.domain.member.dto.MemberReqDTO;
import com.kiri.server.domain.member.dto.MemberResDTO;
import com.kiri.server.domain.member.service.MemberService;
import com.kiri.server.global.apiPayload.handler.GeneralExceptionAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MemberControllerTest {

    @Mock
    private MemberService memberService;

    @InjectMocks
    private MemberController memberController;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(memberController)
                .setControllerAdvice(new GeneralExceptionAdvice())
                .build();
    }

    @Test
    @DisplayName("소셜 로그인 성공")
    void loginSuccess() throws Exception {

        MemberReqDTO.Login request = new MemberReqDTO.Login("GOOGLE", "mock-id-token", "test-nonce");
        MemberResDTO.Login response = new MemberResDTO.Login("access-token", "refresh-token", false, true);

        given(memberService.login(any(MemberReqDTO.Login.class)))
                .willReturn(response);

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("socialProvider가 비어있으면 400")
    void loginWithoutSocialProvider() throws Exception {

        MemberReqDTO.Login request = new MemberReqDTO.Login("", "mock-id-token", "test-nonce");

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(memberService);
    }

    @Test
    @DisplayName("socialProviderToken이 비어있으면 400")
    void loginWithoutSocialProviderToken() throws Exception {

        MemberReqDTO.Login request = new MemberReqDTO.Login("GOOGLE", "", "test-nonce");

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(memberService);
    }

    @Test
    @DisplayName("nonce가 비어있으면 400")
    void loginWithoutNonce() throws Exception {

        MemberReqDTO.Login request = new MemberReqDTO.Login("GOOGLE", "mock-id-token", "");

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(memberService);
    }
}