package com.kiri.server.domain.schedule.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kiri.server.domain.schedule.dto.ScheduleReqDTO;
import com.kiri.server.domain.schedule.dto.ScheduleResDTO;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScheduleCommandController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtProvider.class})
@TestPropertySource(properties = "jwt.secret=" + ScheduleCommandControllerTest.SECRET)
class ScheduleCommandControllerTest {

    static final String SECRET = "test-secret-key-for-jwt-must-be-at-least-32-bytes";
    private static final Long MEMBER_ID = 1L;
    private static final Long BUCKET_ID = 42L;

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
            return new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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

    private ScheduleReqDTO.Create validRequest() {
        return ScheduleReqDTO.Create.builder()
                .scheduleDate(LocalDate.of(2026, 11, 15))
                .scheduleTime(LocalTime.of(14, 0))
                .participantMemberIds(List.of(2L, 3L))
                .requestMessage("같이 가요")
                .build();
    }

    @Test
    @DisplayName("토큰 없이 일정을 생성하면 COMMON401_1로 응답한다.")
    void create_noToken() throws Exception {
        mockMvc.perform(post("/api/v1/bucket-items/{bucketItemId}/schedules", BUCKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(validRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_1"));
    }

    @Test
    @DisplayName("일정 생성에 성공하면 SCHEDULE201_1과 생성 결과를 반환한다.")
    void create_success() throws Exception {
        given(scheduleCommandService.create(eq(MEMBER_ID), eq(BUCKET_ID), any()))
                .willReturn(ScheduleResDTO.Created.builder()
                        .scheduleId(100L).bucketItemId(BUCKET_ID)
                        .scheduleDate(LocalDate.of(2026, 11, 15)).scheduleTime(LocalTime.of(14, 0))
                        .status(ScheduleStatus.PENDING_RESPONSE)
                        .responseDeadline(LocalDateTime.of(2026, 11, 14, 14, 0))
                        .proposerId(MEMBER_ID).participantMemberIds(List.of(2L, 3L)).build());

        mockMvc.perform(post("/api/v1/bucket-items/{bucketItemId}/schedules", BUCKET_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SCHEDULE201_1"))
                .andExpect(jsonPath("$.result.scheduleId").value(100))
                .andExpect(jsonPath("$.result.status").value("PENDING_RESPONSE"))
                .andExpect(jsonPath("$.result.responseDeadline").value("2026-11-14T14:00:00"))
                .andExpect(jsonPath("$.result.participantMemberIds[0]").value(2));
    }

    @Test
    @DisplayName("날짜가 없으면 COMMON400_1로 응답한다.")
    void create_missingDate() throws Exception {
        Map<String, Object> req = Map.of(
                "scheduleTime", "14:00",
                "participantMemberIds", List.of(2L));

        mockMvc.perform(post("/api/v1/bucket-items/{bucketItemId}/schedules", BUCKET_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
    }

    @Test
    @DisplayName("초대 대상이 비어 있으면 COMMON400_1로 응답한다.")
    void create_emptyParticipants() throws Exception {
        Map<String, Object> req = Map.of(
                "scheduleDate", "2026-11-15",
                "scheduleTime", "14:00",
                "participantMemberIds", List.of());

        mockMvc.perform(post("/api/v1/bucket-items/{bucketItemId}/schedules", BUCKET_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
    }

    @Test
    @DisplayName("일정을 만들 수 없는 버킷이면 SCHEDULE409_1로 응답한다.")
    void create_bucketNotSchedulable() throws Exception {
        given(scheduleCommandService.create(eq(MEMBER_ID), eq(BUCKET_ID), any()))
                .willThrow(new ScheduleException(ScheduleErrorCode.BUCKET_NOT_SCHEDULABLE));

        mockMvc.perform(post("/api/v1/bucket-items/{bucketItemId}/schedules", BUCKET_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE409_1"));
    }

    @Test
    @DisplayName("다른 가족의 버킷이면 SCHEDULE403_2로 응답한다.")
    void create_bucketForbidden() throws Exception {
        given(scheduleCommandService.create(eq(MEMBER_ID), eq(BUCKET_ID), any()))
                .willThrow(new ScheduleException(ScheduleErrorCode.BUCKET_FORBIDDEN));

        mockMvc.perform(post("/api/v1/bucket-items/{bucketItemId}/schedules", BUCKET_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHEDULE403_2"));
    }

    @Test
    @DisplayName("토큰 없이 취소하면 COMMON401_1로 응답한다.")
    void cancel_noToken() throws Exception {
        mockMvc.perform(patch("/api/v1/schedules/{scheduleId}/cancel", 100L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_1"));
    }

    @Test
    @DisplayName("취소에 성공하면 SCHEDULE200_3과 취소 결과를 반환한다.")
    void cancel_success() throws Exception {
        given(scheduleCommandService.cancel(eq(MEMBER_ID), eq(100L)))
                .willReturn(ScheduleResDTO.Cancelled.builder()
                        .scheduleId(100L).status(ScheduleStatus.CANCELLED).bucketItemId(BUCKET_ID).build());

        mockMvc.perform(patch("/api/v1/schedules/{scheduleId}/cancel", 100L)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SCHEDULE200_3"))
                .andExpect(jsonPath("$.result.scheduleId").value(100))
                .andExpect(jsonPath("$.result.status").value("CANCELLED"))
                .andExpect(jsonPath("$.result.bucketItemId").value(42));
    }

    @Test
    @DisplayName("제안자가 아니면 SCHEDULE403_4로 응답한다.")
    void cancel_notProposer() throws Exception {
        given(scheduleCommandService.cancel(eq(MEMBER_ID), eq(100L)))
                .willThrow(new ScheduleException(ScheduleErrorCode.NOT_PROPOSER));

        mockMvc.perform(patch("/api/v1/schedules/{scheduleId}/cancel", 100L)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHEDULE403_4"));
    }

    @Test
    @DisplayName("취소할 수 없는 상태면 SCHEDULE409_3으로 응답한다.")
    void cancel_notCancelable() throws Exception {
        given(scheduleCommandService.cancel(eq(MEMBER_ID), eq(100L)))
                .willThrow(new ScheduleException(ScheduleErrorCode.NOT_CANCELABLE));

        mockMvc.perform(patch("/api/v1/schedules/{scheduleId}/cancel", 100L)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE409_3"));
    }
}
