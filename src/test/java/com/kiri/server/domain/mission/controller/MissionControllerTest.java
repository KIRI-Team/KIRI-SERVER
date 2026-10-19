package com.kiri.server.domain.mission.controller;

import com.kiri.server.domain.mission.dto.MissionReqDTO;
import com.kiri.server.domain.mission.dto.MissionResDTO;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.mission.exception.MissionException;
import com.kiri.server.domain.mission.exception.code.MissionErrorCode;
import com.kiri.server.domain.mission.service.MissionQueryService;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MissionController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtProvider.class})
@TestPropertySource(properties = "jwt.secret=" + MissionControllerTest.SECRET)
class MissionControllerTest {

    static final String SECRET = "test-secret-key-for-jwt-must-be-at-least-32-bytes";
    private static final Long MEMBER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private MissionQueryService missionQueryService;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        bearerToken = "Bearer " + jwtProvider.createAccessToken(MEMBER_ID);
    }

    @Test
    @DisplayName("토큰 없이 피드를 조회하면 COMMON401_1로 응답한다.")
    void getFeed_noToken() throws Exception {
        mockMvc.perform(get("/api/v1/missions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_1"));
    }

    @Test
    @DisplayName("피드 조회에 성공하면 MISSION200_1과 카드 목록, 다음 커서를 반환한다.")
    void getFeed_success() throws Exception {
        MissionResDTO.FeedItem item = MissionResDTO.FeedItem.builder()
                .missionId(130L)
                .title("국립현대미술관 서울 관람하기")
                .category(Category.EXHIBITION)
                .missionType(MissionType.PERMANENT)
                .placeName("국립현대미술관 서울")
                .price(0)
                .distance(2.4)
                .build();
        given(missionQueryService.getFeed(any())).willReturn(MissionResDTO.Feed.builder()
                .missions(List.of(item)).nextCursor(130L).hasNext(true).build());

        mockMvc.perform(get("/api/v1/missions").header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MISSION200_1"))
                .andExpect(jsonPath("$.result.missions[0].missionId").value(130))
                .andExpect(jsonPath("$.result.missions[0].category").value("EXHIBITION"))
                .andExpect(jsonPath("$.result.missions[0].distance").value(2.4))
                .andExpect(jsonPath("$.result.nextCursor").value(130))
                .andExpect(jsonPath("$.result.hasNext").value(true));
    }

    @Test
    @DisplayName("쿼리 파라미터를 조회 조건으로 받는다.")
    void getFeed_bindsCondition() throws Exception {
        given(missionQueryService.getFeed(any())).willReturn(MissionResDTO.Feed.builder()
                .missions(List.of()).hasNext(false).build());

        mockMvc.perform(get("/api/v1/missions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .param("category", "EXHIBITION")
                        .param("regionCode", "11200")
                        .param("distance", "3")
                        .param("latitude", "37.5446")
                        .param("longitude", "127.0557")
                        .param("date", "2026-10-18")
                        .param("time", "14:00")
                        .param("isFree", "true")
                        .param("cursor", "128")
                        .param("size", "10"))
                .andExpect(status().isOk());

        ArgumentCaptor<MissionReqDTO.FeedCondition> captor = ArgumentCaptor.forClass(MissionReqDTO.FeedCondition.class);
        verify(missionQueryService).getFeed(captor.capture());
        MissionReqDTO.FeedCondition condition = captor.getValue();
        assertThat(condition.category()).isEqualTo(Category.EXHIBITION);
        assertThat(condition.regionCode()).isEqualTo("11200");
        assertThat(condition.distance()).isEqualTo(3);
        assertThat(condition.latitude()).isEqualTo(37.5446);
        assertThat(condition.longitude()).isEqualTo(127.0557);
        assertThat(condition.date()).isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(condition.time()).isEqualTo(LocalTime.of(14, 0));
        assertThat(condition.isFree()).isTrue();
        assertThat(condition.cursor()).isEqualTo(128L);
        assertThat(condition.size()).isEqualTo(10);
    }

    @Test
    @DisplayName("없는 카테고리 값이면 COMMON400_1로 응답한다.")
    void getFeed_invalidCategory() throws Exception {
        mockMvc.perform(get("/api/v1/missions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .param("category", "CONCERT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
        verifyNoInteractions(missionQueryService);
    }

    @Test
    @DisplayName("조회 개수가 1~50을 벗어나면 COMMON400_1로 응답한다.")
    void getFeed_invalidSize() throws Exception {
        mockMvc.perform(get("/api/v1/missions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
        verifyNoInteractions(missionQueryService);
    }

    @Test
    @DisplayName("시각이 HH:mm 형식이 아니면 COMMON400_1로 응답한다.")
    void getFeed_invalidTime() throws Exception {
        mockMvc.perform(get("/api/v1/missions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .param("time", "2시"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
        verifyNoInteractions(missionQueryService);
    }

    @Test
    @DisplayName("거리 값이 잘못되면 MISSION400_1로 응답한다.")
    void getFeed_invalidDistance() throws Exception {
        given(missionQueryService.getFeed(any())).willThrow(new MissionException(MissionErrorCode.INVALID_DISTANCE));

        mockMvc.perform(get("/api/v1/missions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .param("distance", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSION400_1"));
    }

    @Test
    @DisplayName("상세 조회에 성공하면 MISSION200_2와 운영시간을 HH:mm 형식으로 반환한다.")
    void getDetail_success() throws Exception {
        MissionResDTO.Detail detail = MissionResDTO.Detail.builder()
                .missionId(130L)
                .title("국립현대미술관 서울 관람하기")
                .description("설명")
                .category(Category.EXHIBITION)
                .missionType(MissionType.PERMANENT)
                .status(MissionStatus.ACTIVE)
                .indoorOutdoor(IndoorOutdoor.INDOOR)
                .openingTime(LocalTime.of(10, 0))
                .closingTime(LocalTime.of(18, 0))
                .price(0)
                .place(MissionResDTO.PlaceInfo.builder()
                        .placeId(52L).name("국립현대미술관 서울").latitude(37.5786).longitude(126.98).build())
                .prepareItems(List.of())
                .steps(List.of(MissionResDTO.ChecklistItem.builder().title("상설 전시 관람하기").build()))
                .bucketSaved(false)
                .reviewCount(0)
                .build();
        given(missionQueryService.getDetail(130L)).willReturn(detail);

        mockMvc.perform(get("/api/v1/missions/130").header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MISSION200_2"))
                .andExpect(jsonPath("$.result.openingTime").value("10:00"))
                .andExpect(jsonPath("$.result.closingTime").value("18:00"))
                .andExpect(jsonPath("$.result.place.name").value("국립현대미술관 서울"))
                .andExpect(jsonPath("$.result.steps[0].title").value("상설 전시 관람하기"))
                .andExpect(jsonPath("$.result.bucketSaved").value(false));
    }

    @Test
    @DisplayName("노출할 수 없는 미션이면 MISSION404_1로 응답한다.")
    void getDetail_notFound() throws Exception {
        given(missionQueryService.getDetail(99L)).willThrow(new MissionException(MissionErrorCode.NOT_FOUND));

        mockMvc.perform(get("/api/v1/missions/99").header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MISSION404_1"));
    }

    @Test
    @DisplayName("missionId가 숫자가 아니면 COMMON400_1로 응답한다.")
    void getDetail_invalidId() throws Exception {
        mockMvc.perform(get("/api/v1/missions/abc").header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
        verifyNoInteractions(missionQueryService);
    }
}
