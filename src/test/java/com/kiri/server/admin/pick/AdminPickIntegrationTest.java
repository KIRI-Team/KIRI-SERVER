package com.kiri.server.admin.pick;

import com.kiri.server.admin.AdminFixtures;
import com.kiri.server.admin.AdminTestSupport;
import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.global.security.jwt.JwtProvider;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({AdminTestSupport.class, AdminFixtures.class})
class AdminPickIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AdminTestSupport support;
    @Autowired
    private AdminFixtures fx;
    @Autowired
    private EntityManager em;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String memberToken;
    private Member member;
    private Family family;
    private List<Mission> regular;

    @BeforeEach
    void setUp() {
        adminToken = support.bearer(support.createAdmin("pick-admin"));
        member = fx.member("픽회원");
        family = fx.family("픽가족", member);
        memberToken = "Bearer " + jwtProvider.createAccessToken(member.getId());
        regular = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            regular.add(fx.mission(fx.place("장소" + i), "일반 미션 " + i, MissionStatus.ACTIVE));
        }
        em.flush();
    }

    @Test
    @DisplayName("노출 중인 끼리 픽은 첫 페이지 맨 앞에 순서대로 나오고, 일반 목록과 다음 페이지에는 다시 나오지 않는다.")
    void feed_picksOnTop_noDuplicates() throws Exception {
        Mission second = regular.get(1);
        Mission fourth = regular.get(3);
        Long pickB = createPick(fourth.getId(), "두 번째로 보여요", 2);
        Long pickA = createPick(second.getId(), "첫 번째로 보여요", 1);

        JsonNode page1 = feed(null, 2);
        List<Long> ids1 = missionIds(page1);
        assertThat(ids1).startsWith(second.getId(), fourth.getId());
        assertThat(ids1).hasSize(4);
        assertThat(page1.path("result").path("missions").get(0).path("kiriPick").path("kiriPickId").asLong()).isEqualTo(pickA);
        assertThat(page1.path("result").path("missions").get(1).path("kiriPick").path("kiriPickId").asLong()).isEqualTo(pickB);
        assertThat(page1.path("result").path("missions").get(2).path("kiriPick").isNull()).isTrue();

        List<Long> all = new ArrayList<>(ids1);
        JsonNode next = page1;
        while (next.path("result").path("hasNext").asBoolean()) {
            next = feed(next.path("result").path("nextCursor").asLong(), 2);
            all.addAll(missionIds(next));
        }
        List<Long> expected = regular.stream().map(Mission::getId).toList();
        assertThat(all).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(all).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("필터에 맞지 않거나, 꺼졌거나, 기간 밖이거나, 미션이 비공개인 픽은 상단에 나오지 않는다.")
    void feed_hidesNonExposedPicks() throws Exception {
        Long off = createPick(regular.get(0).getId(), "꺼진 픽", 1);
        mockMvc.perform(patch("/api/admin/kiri-picks/" + off).header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(pickBody(regular.get(0).getId(), "꺼진 픽", 1, false,
                                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.exposure").value("INACTIVE"));
        mockMvc.perform(post("/api/admin/kiri-picks").header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(pickBody(regular.get(1).getId(), "예약 픽", 2, true,
                                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(7))))
                .andExpect(jsonPath("$.result.exposure").value("SCHEDULED"));
        Long hiddenPick = createPick(regular.get(2).getId(), "미션 숨김 픽", 3);
        em.find(Mission.class, regular.get(2).getId()).hide();
        em.flush();

        JsonNode page = feed(null, 20);
        for (JsonNode item : page.path("result").path("missions")) {
            assertThat(item.path("kiriPick").isNull()).isTrue();
        }
        mockMvc.perform(get("/api/admin/kiri-picks/" + hiddenPick).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.result.exposure").value("MISSION_NOT_ACTIVE"));

        // 카테고리 필터가 맞지 않으면 상단에도 없다.
        createPick(regular.get(3).getId(), "전시 픽", 4);
        mockMvc.perform(get("/api/v1/missions").param("category", "OUTDOOR").header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(jsonPath("$.result.missions.length()").value(0));
    }

    @Test
    @DisplayName("비공개 미션은 끼리 픽으로 편성할 수 없다.")
    void create_rejectsInactiveMission() throws Exception {
        Mission hidden = fx.mission(fx.place("숨김"), "숨김 미션", MissionStatus.HIDDEN);
        em.flush();
        mockMvc.perform(post("/api/admin/kiri-picks").header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(pickBody(hidden.getId(), "숨김", 1, true,
                                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN400_5"));
    }

    @Test
    @DisplayName("픽 카드에서 저장한 버킷의 일정·완료·리뷰가 그 픽 하나의 성과로만 집계된다.")
    void stats_attributedOnce() throws Exception {
        Mission mission = regular.get(0);
        Long pick = createPick(mission.getId(), "성과 픽", 1);
        Long otherPick = createPick(mission.getId(), "같은 미션 다른 픽", 2);

        mockMvc.perform(post("/api/v1/missions/" + mission.getId() + "/bucket").param("kiriPickId", pick.toString())
                        .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk());
        em.flush();
        BucketItem bucket = em.createQuery("SELECT b FROM BucketItem b WHERE b.mission.id = :id", BucketItem.class)
                .setParameter("id", mission.getId()).getSingleResult();
        assertThat(bucket.getSourceKiriPickId()).isEqualTo(pick);

        Schedule schedule = fx.schedule(family, bucket, member, LocalDate.of(2026, 11, 1), ScheduleStatus.COMPLETED,
                LocalDateTime.of(2026, 11, 1, 18, 0));
        ScheduleParticipant participant = fx.participant(schedule, member, ParticipantStatus.ACCEPTED);
        fx.review(schedule, participant, 4, true);
        em.flush();

        mockMvc.perform(get("/api/admin/kiri-picks/" + pick + "/stats").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.saves").value(1))
                .andExpect(jsonPath("$.result.schedules").value(1))
                .andExpect(jsonPath("$.result.completedSchedules").value(1))
                .andExpect(jsonPath("$.result.averageStarRating").value(4.0))
                .andExpect(jsonPath("$.result.rejoinIntentRate").value(1.0))
                .andExpect(jsonPath("$.result.impressions").doesNotExist());
        mockMvc.perform(get("/api/admin/kiri-picks/" + otherPick + "/stats").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.result.saves").value(0))
                .andExpect(jsonPath("$.result.completedSchedules").value(0));
    }

    @Test
    @DisplayName("다른 미션의 픽 ID로 저장하면 출처를 남기지 않는다.")
    void bucket_ignoresMismatchedPick() throws Exception {
        Long pick = createPick(regular.get(0).getId(), "다른 미션 픽", 1);
        mockMvc.perform(post("/api/v1/missions/" + regular.get(1).getId() + "/bucket").param("kiriPickId", pick.toString())
                        .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk());
        em.flush();
        BucketItem bucket = em.createQuery("SELECT b FROM BucketItem b WHERE b.mission.id = :id", BucketItem.class)
                .setParameter("id", regular.get(1).getId()).getSingleResult();
        assertThat(bucket.getSourceKiriPickId()).isNull();
    }

    @Test
    @DisplayName("순서를 바꾸면 전달한 순서대로 다시 매긴다.")
    void reorder() throws Exception {
        Long a = createPick(regular.get(0).getId(), "A", 1);
        Long b = createPick(regular.get(1).getId(), "B", 2);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/kiri-picks/order")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kiriPickIds\":[" + b + "," + a + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result[0].kiriPickId").value(b))
                .andExpect(jsonPath("$.result[1].kiriPickId").value(a));
    }

    private Long createPick(Long missionId, String headline, int order) throws Exception {
        String body = mockMvc.perform(post("/api/admin/kiri-picks").header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(pickBody(missionId, headline, order, true,
                                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        em.flush();
        return objectMapper.readTree(body).path("result").path("kiriPickId").asLong();
    }

    private JsonNode feed(Long cursor, int size) throws Exception {
        var request = get("/api/v1/missions").param("size", String.valueOf(size)).header(HttpHeaders.AUTHORIZATION, memberToken);
        if (cursor != null) {
            request.param("cursor", cursor.toString());
        }
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static List<Long> missionIds(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.path("result").path("missions").forEach(m -> ids.add(m.path("missionId").asLong()));
        return ids;
    }

    private static String pickBody(Long missionId, String headline, int order, boolean active,
                                   LocalDateTime startAt, LocalDateTime endAt) {
        return """
                {"missionId":%d,"headline":"%s","description":"이번 주 추천","displayOrder":%d,"active":%s,
                 "startAt":"%s","endAt":"%s"}
                """.formatted(missionId, headline, order, active, startAt.withNano(0), endAt.withNano(0));
    }
}
