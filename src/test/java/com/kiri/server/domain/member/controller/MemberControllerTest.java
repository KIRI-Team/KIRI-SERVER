package com.kiri.server.domain.member.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiri.server.domain.member.dto.MemberReqDTO;
import com.kiri.server.domain.member.dto.MemberResDTO;
import com.kiri.server.domain.member.enums.FamilyRole;
import com.kiri.server.domain.member.exception.code.MemberSuccessCode;
import com.kiri.server.domain.member.service.MemberService;
import com.kiri.server.global.config.SecurityConfig;
import com.kiri.server.global.security.handler.CustomAccessDeniedHandler;
import com.kiri.server.global.security.handler.CustomAuthenticationEntryPoint;
import com.kiri.server.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberController.class)
@Import({
        MemberControllerTest.TestConfig.class,
        SecurityConfig.class
})
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private CustomAuthenticationEntryPoint authenticationEntryPoint;

    @MockitoBean
    private CustomAccessDeniedHandler accessDeniedHandler;

    @MockitoBean
    private JwtProvider jwtProvider;

    @TestConfiguration
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
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

    @Test
    @DisplayName("닉네임 설정 성공")
    void updateNicknameSuccess() throws Exception {
        MemberReqDTO.Nickname request = new MemberReqDTO.Nickname("nickname");

        MemberResDTO.Nickname response = MemberResDTO.Nickname.builder().nickname("nickname").build();

        given(memberService.updateNickname(eq(1L), any(MemberReqDTO.Nickname.class))).willReturn(response);

        mockMvc.perform(
                        patch("/api/v1/members/nickname")
                                .with(authentication(
                                        new UsernamePasswordAuthenticationToken(
                                                1L,
                                                null,
                                                List.of()
                                        )
                                ))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code")
                        .value(MemberSuccessCode.NICKNAME_SET.getCode()))
                .andExpect(jsonPath("$.message")
                        .value(MemberSuccessCode.NICKNAME_SET.getMessage()))
                .andExpect(jsonPath("$.result.nickname")
                        .value("nickname"));

        verify(memberService).updateNickname(eq(1L), any(MemberReqDTO.Nickname.class));
    }

    @Test
    @DisplayName("닉네임 설정 실패 - 닉네임 길이 조건 위반")
    void updateNicknameInvalidLength() throws Exception {
        MemberReqDTO.Nickname request = new MemberReqDTO.Nickname("a");

        mockMvc.perform(patch("/api/v1/members/nickname")
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(
                                        1L,
                                        null,
                                        List.of()
                                )
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."))
                .andExpect(jsonPath("$.result.nickname")
                        .value("닉네임은 2~12자로 입력해주세요."));

        verify(memberService, never()).updateNickname(anyLong(), any(MemberReqDTO.Nickname.class));
    }

    @Test
    @DisplayName("닉네임 설정 실패 - 허용되지 않는 문자 포함")
    void updateNicknameInvalidCharacter() throws Exception {
        MemberReqDTO.Nickname request =
                new MemberReqDTO.Nickname("nickname!");

        mockMvc.perform(patch("/api/v1/members/nickname")
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(
                                        1L,
                                        null,
                                        List.of()
                                )
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.result.nickname")
                        .value("닉네임은 한글, 영문, 숫자만 사용할 수 있습니다."));

        verify(memberService, never()).updateNickname(anyLong(), any(MemberReqDTO.Nickname.class));
    }

    @Test
    @DisplayName("닉네임 설정 실패 - 닉네임 미입력")
    void updateNicknameBlank() throws Exception {
        MemberReqDTO.Nickname request = new MemberReqDTO.Nickname("");

        mockMvc.perform(patch("/api/v1/members/nickname")
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(
                                        1L,
                                        null,
                                        List.of()
                                )
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400_1"));

        verify(memberService, never()).updateNickname(anyLong(), any(MemberReqDTO.Nickname.class));
    }

    @Test
    @DisplayName("가족 역할 선택 성공")
    void selectFamilyRoleSuccess() throws Exception {
        MemberReqDTO.SelectFamilyRole request =
                new MemberReqDTO.SelectFamilyRole(FamilyRole.FATHER);

        MemberResDTO.SelectFamilyRole response =
                MemberResDTO.SelectFamilyRole.builder()
                        .familyRole(FamilyRole.FATHER)
                        .build();

        given(memberService.selectFamilyRole(eq(1L), any(MemberReqDTO.SelectFamilyRole.class)))
                .willReturn(response);

        mockMvc.perform(
                        patch("/api/v1/members/family-role")
                                .with(authentication(
                                        new UsernamePasswordAuthenticationToken(
                                                1L,
                                                null,
                                                List.of()
                                        )
                                ))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code")
                        .value(MemberSuccessCode.FAMILY_ROLE_SET.getCode()))
                .andExpect(jsonPath("$.message")
                        .value(MemberSuccessCode.FAMILY_ROLE_SET.getMessage()))
                .andExpect(jsonPath("$.result.familyRole")
                        .value("FATHER"));

        verify(memberService)
                .selectFamilyRole(eq(1L), any(MemberReqDTO.SelectFamilyRole.class));
    }

    @Test
    @DisplayName("가족 역할 설정 실패 - 가족 역할 미입력")
    void updateFamilyRoleBlank() throws Exception {
        String request = """
            {
                "familyRole": null
            }
            """;

        mockMvc.perform(
                        patch("/api/v1/members/family-role")
                                .with(authentication(
                                        new UsernamePasswordAuthenticationToken(
                                                1L,
                                                null,
                                                List.of()
                                        )
                                ))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));

        verify(memberService, never())
                .selectFamilyRole(anyLong(), any(MemberReqDTO.SelectFamilyRole.class));
    }

    @Test
    @DisplayName("가족 역할 설정 실패 - 허용되지 않은 가족 역할")
    void updateFamilyRoleInvalidRole() throws Exception {
        String request = """
            {
                "familyRole": "BROTHER"
            }
            """;

        mockMvc.perform(
                        patch("/api/v1/members/family-role")
                                .with(authentication(
                                        new UsernamePasswordAuthenticationToken(
                                                1L,
                                                null,
                                                List.of()
                                        )
                                ))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400_1"));

        verify(memberService, never())
                .selectFamilyRole(anyLong(), any(MemberReqDTO.SelectFamilyRole.class));
    }
}