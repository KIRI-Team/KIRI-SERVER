package com.kiri.server.domain.family.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiri.server.domain.family.dto.FamilyReqDTO;
import com.kiri.server.domain.family.dto.FamilyResDTO;
import com.kiri.server.domain.family.exception.code.FamilySuccessCode;
import com.kiri.server.domain.family.service.FamilyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(FamilyController.class)
@Import(FamilyControllerTest.TestConfig.class)
class FamilyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FamilyService familyService;

    @TestConfiguration
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Test
    @DisplayName("가족 공간 생성 성공")
    void createFamilySuccess() throws Exception{
        FamilyReqDTO.Create request = new FamilyReqDTO.Create("끼리네 가족");

        FamilyResDTO.Create response = FamilyResDTO.Create.builder()
                .familyId(1L)
                .name("끼리네 가족")
                .inviteCode("ABC123")
                .inviteUrl("https://kiri.app/invite/test-token")
                .inviteExpiresAt(LocalDateTime.now().plusDays(7))
                .build();

        given(familyService.createFamily(anyLong(), any(FamilyReqDTO.Create.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/v1/families")
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(
                                        1L,
                                        null,
                                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                                )
                        ))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value(FamilySuccessCode.FAMILY_CREATED.getCode()))
                .andExpect(jsonPath("$.message").value(FamilySuccessCode.FAMILY_CREATED.getMessage()))
                .andExpect(jsonPath("$.result.familyId").value(1))
                .andExpect(jsonPath("$.result.name").value("끼리네 가족"))
                .andExpect(jsonPath("$.result.inviteCode").value("ABC123"))
                .andExpect(jsonPath("$.result.inviteUrl")
                        .value("https://kiri.app/invite/test-token"))
                .andExpect(jsonPath("$.result.inviteExpiresAt").exists());
    }

    @Test
    @DisplayName("가족 이름이 비어있으면 가족 공간 생성 실패")
    @WithMockUser
    void createFamilyEmptyName() throws Exception{
        FamilyReqDTO.Create request = new FamilyReqDTO.Create("");

        mockMvc.perform(post("/api/v1/families")
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("가족 이름이 1자이면 가족 공간 생성 실패")
    @WithMockUser
    void createFamilyNameTooShort() throws Exception{
        FamilyReqDTO.Create request = new FamilyReqDTO.Create("가");

        mockMvc.perform(post("/api/v1/families")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("가족 이름이 20자를 초과하면 가족 공간 생성 실패")
    @WithMockUser
    void createFamilyNameTooLong() throws Exception{
        FamilyReqDTO.Create request = new FamilyReqDTO.Create("가나다라마바사아자차카타파하가나다라마바사아");

        mockMvc.perform(post("/api/v1/families")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}