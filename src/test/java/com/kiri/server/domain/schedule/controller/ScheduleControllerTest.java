package com.kiri.server.domain.schedule.controller;

import com.kiri.server.domain.schedule.dto.ScheduleReqDTO;
import com.kiri.server.domain.schedule.dto.ScheduleResDTO;
import com.kiri.server.domain.schedule.enums.CalendarMyStatus;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.domain.schedule.exception.ScheduleException;
import com.kiri.server.domain.schedule.exception.code.ScheduleErrorCode;
import com.kiri.server.domain.schedule.service.ScheduleQueryService;
import com.kiri.server.global.config.SecurityConfig;
import com.kiri.server.global.security.handler.CustomAccessDeniedHandler;
import com.kiri.server.global.security.handler.CustomAuthenticationEntryPoint;
import com.kiri.server.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScheduleController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtProvider.class})
@TestPropertySource(properties = "jwt.secret=" + ScheduleControllerTest.SECRET)
class ScheduleControllerTest {

    static final String SECRET = "test-secret-key-for-jwt-must-be-at-least-32-bytes";
    private static final Long MEMBER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private ScheduleQueryService scheduleQueryService;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        bearerToken = "Bearer " + jwtProvider.createAccessToken(MEMBER_ID);
    }

    @Test
    @DisplayName("토큰 없이 캘린더를 조회하면 COMMON401_1로 응답한다.")
    void getCalendar_noToken() throws Exception {
        mockMvc.perform(get("/api/v1/schedules/calendar").param("year", "2026").param("month", "10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_1"));
    }

    @Test
    @DisplayName("캘린더 조회에 성공하면 SCHEDULE200_1과 일정 목록을 반환한다.")
    void getCalendar_success() throws Exception {
        ScheduleResDTO.CalendarItem item = ScheduleResDTO.CalendarItem.builder()
                .scheduleId(100L)
                .date(LocalDate.of(2026, 10, 3))
                .time(LocalTime.of(14, 50))
                .status(ScheduleStatus.CONFIRMED)
                .myStatus(CalendarMyStatus.ACCEPTED)
                .build();
        given(scheduleQueryService.getCalendar(any(), any())).willReturn(ScheduleResDTO.Calendar.builder()
                .year(2026).month(10).schedules(List.of(item)).build());

        mockMvc.perform(get("/api/v1/schedules/calendar")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .param("year", "2026")
                        .param("month", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SCHEDULE200_1"))
                .andExpect(jsonPath("$.result.year").value(2026))
                .andExpect(jsonPath("$.result.month").value(10))
                .andExpect(jsonPath("$.result.schedules[0].scheduleId").value(100))
                .andExpect(jsonPath("$.result.schedules[0].date").value("2026-10-03"))
                .andExpect(jsonPath("$.result.schedules[0].time").value("14:50"))
                .andExpect(jsonPath("$.result.schedules[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.result.schedules[0].myStatus").value("ACCEPTED"));
    }

    @Test
    @DisplayName("로그인 회원 ID와 연·월을 조회 조건으로 받는다.")
    void getCalendar_bindsCondition() throws Exception {
        given(scheduleQueryService.getCalendar(any(), any())).willReturn(ScheduleResDTO.Calendar.builder()
                .year(2026).month(10).schedules(List.of()).build());

        mockMvc.perform(get("/api/v1/schedules/calendar")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .param("year", "2026")
                        .param("month", "10"))
                .andExpect(status().isOk());

        ArgumentCaptor<ScheduleReqDTO.CalendarCondition> captor = ArgumentCaptor.forClass(ScheduleReqDTO.CalendarCondition.class);
        verify(scheduleQueryService).getCalendar(eq(MEMBER_ID), captor.capture());
        assertThat(captor.getValue().year()).isEqualTo(2026);
        assertThat(captor.getValue().month()).isEqualTo(10);
    }

    @Test
    @DisplayName("month가 1~12 범위를 벗어나면 COMMON400_1로 응답한다.")
    void getCalendar_invalidMonth() throws Exception {
        mockMvc.perform(get("/api/v1/schedules/calendar")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .param("year", "2026")
                        .param("month", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
    }

    @Test
    @DisplayName("year·month가 없으면 COMMON400_1로 응답한다.")
    void getCalendar_missingParams() throws Exception {
        mockMvc.perform(get("/api/v1/schedules/calendar").header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
    }

    @Test
    @DisplayName("가족공간이 없으면 SCHEDULE403_1로 응답한다.")
    void getCalendar_noFamily() throws Exception {
        given(scheduleQueryService.getCalendar(any(), any()))
                .willThrow(new ScheduleException(ScheduleErrorCode.FAMILY_REQUIRED));

        mockMvc.perform(get("/api/v1/schedules/calendar")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .param("year", "2026")
                        .param("month", "10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHEDULE403_1"));
    }
}
