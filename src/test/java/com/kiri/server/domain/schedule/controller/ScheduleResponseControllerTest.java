package com.kiri.server.domain.schedule.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiri.server.domain.schedule.dto.ScheduleResDTO;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.domain.schedule.exception.ScheduleException;
import com.kiri.server.domain.schedule.exception.code.ScheduleErrorCode;
import com.kiri.server.domain.schedule.service.ScheduleCommandService;
import com.kiri.server.domain.schedule.service.ScheduleResponseService;
import com.kiri.server.global.config.SecurityConfig;
import com.kiri.server.global.security.handler.CustomAccessDeniedHandler;
import com.kiri.server.global.security.handler.CustomAuthenticationEntryPoint;
import com.kiri.server.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScheduleCommandController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtProvider.class})
@TestPropertySource(properties = "jwt.secret=" + ScheduleResponseControllerTest.SECRET)
class ScheduleResponseControllerTest {

    static final String SECRET = "test-secret-key-for-jwt-must-be-at-least-32-bytes";
    private static final Long MEMBER_ID = 2L;
    private static final Long SCHEDULE_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ScheduleCommandService scheduleCommandService;

    @MockitoBean
    private ScheduleResponseService scheduleResponseService;

    @TestConfiguration
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private String bearerToken;

    @BeforeEach
    void setUp() {
        bearerToken = "Bearer " + jwtProvider.createAccessToken(MEMBER_ID);
    }

    private String body(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    @DisplayName("토큰 없이 응답하면 COMMON401_1로 응답한다.")
    void respond_noToken() throws Exception {
        mockMvc.perform(patch("/api/v1/schedules/{scheduleId}/response", SCHEDULE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("action", "ACCEPT"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_1"));
    }

    @Test
    @DisplayName("수락에 성공하면 SCHEDULE200_2와 내 응답·일정 상태를 반환한다.")
    void respond_success() throws Exception {
        given(scheduleResponseService.respond(eq(MEMBER_ID), eq(SCHEDULE_ID), any()))
                .willReturn(ScheduleResDTO.Responded.builder()
                        .scheduleId(SCHEDULE_ID).myStatus(ParticipantStatus.ACCEPTED).status(ScheduleStatus.CONFIRMED).build());

        mockMvc.perform(patch("/api/v1/schedules/{scheduleId}/response", SCHEDULE_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("action", "ACCEPT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SCHEDULE200_2"))
                .andExpect(jsonPath("$.result.scheduleId").value(100))
                .andExpect(jsonPath("$.result.myStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.result.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("action이 없으면 COMMON400_1로 응답한다.")
    void respond_missingAction() throws Exception {
        mockMvc.perform(patch("/api/v1/schedules/{scheduleId}/response", SCHEDULE_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
    }

    @Test
    @DisplayName("초대받지 않은 일정이면 SCHEDULE403_3으로 응답한다.")
    void respond_notInvited() throws Exception {
        given(scheduleResponseService.respond(eq(MEMBER_ID), eq(SCHEDULE_ID), any()))
                .willThrow(new ScheduleException(ScheduleErrorCode.NOT_INVITED));

        mockMvc.perform(patch("/api/v1/schedules/{scheduleId}/response", SCHEDULE_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("action", "ACCEPT"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHEDULE403_3"));
    }

    @Test
    @DisplayName("응답 마감이 지난 일정이면 SCHEDULE409_2로 응답한다.")
    void respond_closed() throws Exception {
        given(scheduleResponseService.respond(eq(MEMBER_ID), eq(SCHEDULE_ID), any()))
                .willThrow(new ScheduleException(ScheduleErrorCode.RESPONSE_CLOSED));

        mockMvc.perform(patch("/api/v1/schedules/{scheduleId}/response", SCHEDULE_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("action", "DECLINE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE409_2"));
    }
}
