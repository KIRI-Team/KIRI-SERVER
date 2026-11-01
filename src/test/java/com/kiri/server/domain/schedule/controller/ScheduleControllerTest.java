package com.kiri.server.domain.schedule.controller;

import com.kiri.server.domain.schedule.dto.ScheduleReqDTO;
import com.kiri.server.domain.schedule.dto.ScheduleResDTO;
import com.kiri.server.domain.schedule.enums.CalendarMyStatus;
import com.kiri.server.domain.schedule.enums.ConditionType;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.domain.schedule.enums.UpcomingCardType;
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

    @Test
    @DisplayName("토큰 없이 다가오는 일정을 조회하면 COMMON401_1로 응답한다.")
    void getUpcoming_noToken() throws Exception {
        mockMvc.perform(get("/api/v1/schedules/upcoming"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_1"));
    }

    @Test
    @DisplayName("다가오는 일정 조회에 성공하면 SCHEDULE200_5와 두 슬롯을 반환한다.")
    void getUpcoming_success() throws Exception {
        ScheduleResDTO.UpcomingCard responseRequired = ScheduleResDTO.UpcomingCard.builder()
                .scheduleId(120L)
                .date(LocalDate.of(2026, 11, 5))
                .time(LocalTime.of(18, 0))
                .status(ScheduleStatus.PENDING_RESPONSE)
                .myStatus(CalendarMyStatus.PENDING)
                .dDay(4)
                .type(UpcomingCardType.RESPONSE_REQUIRED)
                .title("광장시장에서 시장음식 먹어보기")
                .build();
        ScheduleResDTO.UpcomingCard upcoming = ScheduleResDTO.UpcomingCard.builder()
                .scheduleId(118L)
                .date(LocalDate.of(2026, 11, 3))
                .time(LocalTime.of(14, 50))
                .status(ScheduleStatus.CONFIRMED)
                .myStatus(CalendarMyStatus.ACCEPTED)
                .dDay(2)
                .type(UpcomingCardType.UPCOMING)
                .title("한강 뚜벅뚜벅 축제 가보기")
                .build();
        given(scheduleQueryService.getUpcoming(any())).willReturn(ScheduleResDTO.UpcomingHome.builder()
                .responseRequired(responseRequired).upcoming(upcoming).build());

        mockMvc.perform(get("/api/v1/schedules/upcoming")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SCHEDULE200_5"))
                .andExpect(jsonPath("$.result.responseRequired.scheduleId").value(120))
                .andExpect(jsonPath("$.result.responseRequired.type").value("RESPONSE_REQUIRED"))
                .andExpect(jsonPath("$.result.responseRequired.myStatus").value("PENDING"))
                .andExpect(jsonPath("$.result.responseRequired.dDay").value(4))
                .andExpect(jsonPath("$.result.upcoming.scheduleId").value(118))
                .andExpect(jsonPath("$.result.upcoming.type").value("UPCOMING"))
                .andExpect(jsonPath("$.result.upcoming.date").value("2026-11-03"))
                .andExpect(jsonPath("$.result.upcoming.time").value("14:50"));
    }

    @Test
    @DisplayName("로그인 회원 ID를 다가오는 일정 조회에 전달한다.")
    void getUpcoming_bindsMemberId() throws Exception {
        given(scheduleQueryService.getUpcoming(any()))
                .willReturn(ScheduleResDTO.UpcomingHome.builder().build());

        mockMvc.perform(get("/api/v1/schedules/upcoming")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk());

        verify(scheduleQueryService).getUpcoming(eq(MEMBER_ID));
    }

    @Test
    @DisplayName("가족공간이 없으면 다가오는 일정 조회도 SCHEDULE403_1로 응답한다.")
    void getUpcoming_noFamily() throws Exception {
        given(scheduleQueryService.getUpcoming(any()))
                .willThrow(new ScheduleException(ScheduleErrorCode.FAMILY_REQUIRED));

        mockMvc.perform(get("/api/v1/schedules/upcoming")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHEDULE403_1"));
    }

    @Test
    @DisplayName("로그인 회원 ID와 경로의 일정 ID로 조회해 SCHEDULE200_6과 미션 정보·적용 조건을 반환한다.")
    void getConditions_success() throws Exception {
        given(scheduleQueryService.getConditions(MEMBER_ID, 118L)).willReturn(ScheduleResDTO.Conditions.builder()
                .missionTitle("한강 뚜벅뚜벅 축제 가보기")
                .estimatedStepsMin(5000)
                .estimatedStepsMax(12000)
                .durationMinMinutes(60)
                .durationMaxMinutes(180)
                .conditions(List.of(ConditionType.REDUCE_STEPS, ConditionType.MORE_REST))
                .build());

        mockMvc.perform(get("/api/v1/schedules/118/conditions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SCHEDULE200_6"))
                .andExpect(jsonPath("$.result.missionTitle").value("한강 뚜벅뚜벅 축제 가보기"))
                .andExpect(jsonPath("$.result.estimatedStepsMin").value(5000))
                .andExpect(jsonPath("$.result.estimatedStepsMax").value(12000))
                .andExpect(jsonPath("$.result.durationMinMinutes").value(60))
                .andExpect(jsonPath("$.result.durationMaxMinutes").value(180))
                .andExpect(jsonPath("$.result.conditions[0]").value("REDUCE_STEPS"))
                .andExpect(jsonPath("$.result.conditions[1]").value("MORE_REST"));
    }

    @Test
    @DisplayName("없거나 다른 가족의 일정이면 SCHEDULE404_2로 응답한다.")
    void getConditions_scheduleNotFound() throws Exception {
        given(scheduleQueryService.getConditions(any(), any()))
                .willThrow(new ScheduleException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/schedules/118/conditions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHEDULE404_2"));
    }

    @Test
    @DisplayName("로그인 회원 ID와 경로의 일정 ID로 조회해 SCHEDULE200_8과 추천 장소 목록을 반환한다.")
    void getNearby_success() throws Exception {
        given(scheduleQueryService.getNearby(MEMBER_ID, 118L)).willReturn(List.of(
                ScheduleResDTO.NearbyItem.builder()
                        .placeName("세빛섬")
                        .region("서울시 서초구")
                        .description("한강 위에서 야경을 보기 좋아요")
                        .imageUrl("https://image/sevitseom")
                        .build()));

        mockMvc.perform(get("/api/v1/schedules/118/nearby")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SCHEDULE200_8"))
                .andExpect(jsonPath("$.result[0].placeName").value("세빛섬"))
                .andExpect(jsonPath("$.result[0].region").value("서울시 서초구"))
                .andExpect(jsonPath("$.result[0].description").value("한강 위에서 야경을 보기 좋아요"))
                .andExpect(jsonPath("$.result[0].imageUrl").value("https://image/sevitseom"));
    }

    @Test
    @DisplayName("없거나 다른 가족의 일정이면 근처 놀거리 조회도 SCHEDULE404_2로 응답한다.")
    void getNearby_scheduleNotFound() throws Exception {
        given(scheduleQueryService.getNearby(any(), any()))
                .willThrow(new ScheduleException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/schedules/118/nearby")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHEDULE404_2"));
    }
}
