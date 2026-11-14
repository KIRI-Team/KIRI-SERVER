package com.kiri.server.domain.suggestion.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiri.server.domain.suggestion.dto.SuggestionReqDTO;
import com.kiri.server.domain.suggestion.dto.SuggestionResDTO;
import com.kiri.server.domain.suggestion.service.SuggestionService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SuggestionController.class)
@Import(SuggestionControllerTest.TestConfig.class)
class SuggestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SuggestionService suggestionService;

    @TestConfiguration
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private static final Long MEMBER_ID = 1L;

    @Test
    @DisplayName("건의 작성에 성공한다.")
    void createSuggestionSuccess() throws Exception {
        SuggestionReqDTO.Create request = new SuggestionReqDTO.Create("서비스 개선 요청입니다.", "suggestions/test.png");

        SuggestionResDTO.Create response = SuggestionResDTO.Create.builder()
                .suggestionId(1L)
                .build();

        given(suggestionService.createSuggestion(eq(MEMBER_ID), any(SuggestionReqDTO.Create.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/suggestions")
                        .with(authentication(new UsernamePasswordAuthenticationToken(MEMBER_ID, null, List.of())))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.suggestionId").value(1));
    }

    @Test
    @DisplayName("이미지 없이 건의 작성에 성공한다.")
    void createSuggestionWithoutImageSuccess() throws Exception {
        SuggestionReqDTO.Create request = new SuggestionReqDTO.Create("이미지 없는 건의입니다.", null);

        SuggestionResDTO.Create response = SuggestionResDTO.Create.builder()
                .suggestionId(2L)
                .build();

        given(suggestionService.createSuggestion(eq(MEMBER_ID), any(SuggestionReqDTO.Create.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/suggestions")
                        .with(authentication(new UsernamePasswordAuthenticationToken(MEMBER_ID, null, List.of())))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.suggestionId").value(2));
    }

    @Test
    @DisplayName("건의 내용이 비어 있으면 작성에 실패한다.")
    void createSuggestionWithEmptyContent() throws Exception {
        SuggestionReqDTO.Create request = new SuggestionReqDTO.Create("", null);

        mockMvc.perform(post("/api/v1/suggestions")
                        .with(authentication(new UsernamePasswordAuthenticationToken(MEMBER_ID, null, List.of())))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("건의 내용이 500자를 초과하면 작성에 실패한다.")
    void createSuggestionWithContentOver500Characters() throws Exception {
        SuggestionReqDTO.Create request = new SuggestionReqDTO.Create("가".repeat(501), null);

        mockMvc.perform(post("/api/v1/suggestions")
                        .with(authentication(new UsernamePasswordAuthenticationToken(MEMBER_ID, null, List.of())))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}