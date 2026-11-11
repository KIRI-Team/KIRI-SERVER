package com.kiri.server.admin.sync;

import com.kiri.server.admin.AdminFixtures;
import com.kiri.server.admin.AdminTestSupport;
import com.kiri.server.admin.sync.dto.SyncAdminDTO;
import com.kiri.server.admin.sync.service.AdminSyncRunner;
import com.kiri.server.domain.external.entity.ExternalSyncRetry;
import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.repository.ExternalSyncStateRepository;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.place.entity.Place;
import com.kiri.server.domain.place.enums.PlaceType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({AdminTestSupport.class, AdminFixtures.class})
class AdminSyncIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AdminTestSupport support;
    @Autowired
    private AdminFixtures fx;
    @Autowired
    private ExternalSyncStateRepository stateRepository;
    @Autowired
    private EntityManager em;
    @Autowired
    private java.time.Clock clock;

    // 실제 외부 API를 부르지 않도록 비동기 실행기는 가짜로 둔다.
    @MockitoBean
    private AdminSyncRunner adminSyncRunner;

    private String token;

    @BeforeEach
    void setUp() {
        token = support.bearer(support.createAdmin("sync-admin"));
    }

    @Test
    @DisplayName("수동 실행은 202로 접수하고 실행기를 호출한다.")
    void run_accepted() throws Exception {
        mockMvc.perform(post("/api/admin/sync/TOUR_API/run").param("mode", "FULL_CHECK").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.result.mode").value("FULL_CHECK"));
        verify(adminSyncRunner).runTourApi(SyncAdminDTO.RunMode.FULL_CHECK);
    }

    @Test
    @DisplayName("수집기가 없는 출처는 400이다.")
    void run_unsupportedSource() throws Exception {
        mockMvc.perform(post("/api/admin/sync/CULTURE_PORTAL/run").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN400_4"));
    }

    @Test
    @DisplayName("이미 실행 중이면 409이고 실행기를 부르지 않는다.")
    void run_alreadyRunning() throws Exception {
        mockMvc.perform(get("/api/admin/sync/sources").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
        em.flush();
        em.createQuery("UPDATE ExternalSyncState s SET s.runningSince = :now WHERE s.externalSource = :source")
                .setParameter("now", LocalDateTime.now(clock)).setParameter("source", ExternalSource.TOUR_API).executeUpdate();
        em.clear();

        mockMvc.perform(post("/api/admin/sync/TOUR_API/run").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN409_3"));
        verify(adminSyncRunner, never()).runTourApi(any());
    }

    @Test
    @DisplayName("중단은 사유가 필수이고, 중단하면 현황에 표시되며 수동 실행이 막힌다. 재개하면 풀린다.")
    void pauseAndResume() throws Exception {
        mockMvc.perform(post("/api/admin/sync/TOUR_API/pause").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN400_1"));

        mockMvc.perform(post("/api/admin/sync/TOUR_API/pause").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"API 키 교체\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.adminPaused").value(true));
        em.flush();
        assertThat(stateRepository.findByExternalSource(ExternalSource.TOUR_API).orElseThrow().isAdminPaused()).isTrue();

        mockMvc.perform(post("/api/admin/sync/TOUR_API/run").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN400_5"));

        mockMvc.perform(post("/api/admin/sync/TOUR_API/resume").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.adminPaused").value(false));
    }

    @Test
    @DisplayName("한도를 넘긴 실패 건을 재처리 요청하면 시도 횟수가 0이 된다.")
    void retry_resetsAttempts() throws Exception {
        ExternalSyncRetry retry = ExternalSyncRetry.builder().externalSource(ExternalSource.TOUR_API)
                .externalId("ext-1").rawItem("{\"contentid\":\"ext-1\"}").build();
        em.persist(retry);
        retry.recordFailure("{\"contentid\":\"ext-1\"}", "timeout");
        retry.recordFailure("{\"contentid\":\"ext-1\"}", "timeout");
        retry.recordFailure("{\"contentid\":\"ext-1\"}", "timeout");
        em.flush();

        mockMvc.perform(get("/api/admin/sync/retries").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.result[0].exhausted").value(true));

        mockMvc.perform(post("/api/admin/sync/retries/" + retry.getId() + "/retry").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        em.flush();
        em.clear();
        assertThat(em.find(ExternalSyncRetry.class, retry.getId()).getAttemptCount()).isZero();
    }

    @Test
    @DisplayName("품질 점검은 좌표가 국내 범위를 벗어난 공개 미션을 찾는다.")
    void quality_invalidCoordinate() throws Exception {
        Place broken = Place.builder().name("좌표 오류 장소").placeType(PlaceType.EVENT_VENUE)
                .latitude(new BigDecimal("0.0000000")).longitude(new BigDecimal("0.0000000")).build();
        em.persist(broken);
        Long missionId = fx.mission(broken, "좌표 오류 미션", MissionStatus.ACTIVE).getId();
        em.flush();

        mockMvc.perform(get("/api/admin/sync/quality").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result[?(@.key == 'INVALID_COORDINATE')].samples[0].id").value(missionId.intValue()));
    }
}
