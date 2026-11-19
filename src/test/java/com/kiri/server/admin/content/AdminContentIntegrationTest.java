package com.kiri.server.admin.content;

import com.kiri.server.admin.AdminFixtures;
import com.kiri.server.admin.AdminTestSupport;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.LockedField;
import com.kiri.server.domain.mission.enums.MissionOrigin;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.place.entity.Place;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import com.kiri.server.domain.question.entity.Question;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({AdminTestSupport.class, AdminFixtures.class})
class AdminContentIntegrationTest {

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

    private String token;

    @BeforeEach
    void setUp() {
        token = support.bearer(support.createAdmin("content-admin"));
    }

    @Test
    @DisplayName("수동 등록은 초안으로 저장되고 앱 피드에 나오지 않다가, 공개하면 나온다.")
    void manualMission_draftThenPublish() throws Exception {
        Long missionId = createMission(form("관리자 등록 전시 관람하기", "[]"));
        Mission saved = em.find(Mission.class, missionId);
        assertThat(saved.getStatus()).isEqualTo(MissionStatus.DRAFT);
        assertThat(saved.getOrigin()).isEqualTo(MissionOrigin.MANUAL);

        String memberToken = memberToken();
        mockMvc.perform(get("/api/v1/missions").param("size", "50").header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(jsonPath("$.result.missions[*].missionId", not(hasItem(missionId.intValue()))));

        mockMvc.perform(get("/api/admin/missions/" + missionId + "/preview").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("DRAFT"));

        // 필수 활동이 없어 공개할 수 없다.
        mockMvc.perform(post("/api/admin/missions/" + missionId + "/publish").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN400_3"))
                .andExpect(jsonPath("$.result[0].field").value("checklist"));

        mockMvc.perform(patch("/api/admin/missions/" + missionId).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"form\":" + form("관리자 등록 전시 관람하기", STEPS) + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/missions/" + missionId + "/publish").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("ACTIVE"));
        em.flush();

        mockMvc.perform(get("/api/v1/missions").param("size", "50").header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(jsonPath("$.result.missions[*].missionId", hasItem(missionId.intValue())));
    }

    @Test
    @DisplayName("공개 중 미션 수정은 사유가 필수이고, 바뀐 필드는 잠기며 잠금을 풀 수 있다.")
    void updateActive_requiresReason_andLocks() throws Exception {
        Mission mission = fx.mission(fx.place("서울숲"), "외부 미션", MissionStatus.ACTIVE);
        em.flush();
        JsonNode current = objectMapper.readTree(mockMvc.perform(get("/api/admin/missions/" + mission.getId())
                .header(HttpHeaders.AUTHORIZATION, token)).andReturn().getResponse().getContentAsString());
        String edited = current.path("result").path("form").toString()
                .replace("\"title\":\"외부 미션\"", "\"title\":\"운영자 수정 제목\"")
                .replace("\"checklist\":[]", "\"checklist\":" + STEPS);

        mockMvc.perform(patch("/api/admin/missions/" + mission.getId()).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"form\":" + edited + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN400_1"));
        mockMvc.perform(patch("/api/admin/missions/" + mission.getId()).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"form\":" + edited + ",\"reason\":\"오타 수정\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.form.title").value("운영자 수정 제목"))
                .andExpect(jsonPath("$.result.lockedFields", hasItem("TITLE")))
                .andExpect(jsonPath("$.result.lockedFields", hasItem("CHECKLIST")))
                .andExpect(jsonPath("$.result.lockedFields", not(hasItem("CATEGORY"))));

        mockMvc.perform(delete("/api/admin/missions/" + mission.getId() + "/locked-fields/TITLE").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.lockedFields", not(hasItem("TITLE"))));
    }

    @Test
    @DisplayName("숨긴 미션을 복구하면 공개되고, 기간이 지난 미션은 종료 상태로 돌아간다.")
    void hideAndRestore() throws Exception {
        Mission mission = fx.mission(fx.place("남산"), "숨길 미션", MissionStatus.ACTIVE);
        em.flush();

        mockMvc.perform(post("/api/admin/missions/" + mission.getId() + "/hide").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"신고 접수\"}"))
                .andExpect(jsonPath("$.result.status").value("HIDDEN"));
        mockMvc.perform(post("/api/admin/missions/" + mission.getId() + "/restore").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"확인 완료\"}"))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"));
        mockMvc.perform(post("/api/admin/missions/" + mission.getId() + "/restore").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"중복 요청\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN400_5"));
    }

    @Test
    @DisplayName("미션 목록은 출처와 상태로 거른다.")
    void searchMissions() throws Exception {
        createMission(form("수동 등록 검색용", "[]"));
        fx.mission(fx.place("외부"), "외부 검색용", MissionStatus.ACTIVE);
        em.flush();

        mockMvc.perform(get("/api/admin/missions").param("origin", "MANUAL").param("keyword", "검색용")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.content[0].title").value("수동 등록 검색용"));
    }

    @Test
    @DisplayName("장소를 고치면 바뀐 필드가 잠겨 재수집 이미지가 덮어쓰지 않는다.")
    void updatePlace_locks() throws Exception {
        Place place = fx.place("성수 팝업존");
        em.flush();

        mockMvc.perform(patch("/api/admin/places/" + place.getId()).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"성수 팝업존","placeType":"CULTURE_FACILITY","regionCode":"11200",
                                 "latitude":37.5444,"longitude":127.0374,"imageUrl":"https://cdn.kiri.test/public/content/a.jpg"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.lockedFields", hasItem("IMAGE")))
                .andExpect(jsonPath("$.result.lockedFields", not(hasItem("NAME"))));
        em.flush();

        Place saved = em.find(Place.class, place.getId());
        saved.refreshImage("https://tour.example/other.jpg", "TOUR_API");
        assertThat(saved.getImageUrl()).isEqualTo("https://cdn.kiri.test/public/content/a.jpg");
        assertThat(saved.isLocked(LockedField.IMAGE)).isTrue();
    }

    @Test
    @DisplayName("가족에게 배정된 질문은 문구를 바꿀 수 없고 중지만 할 수 있다.")
    void question_assignedCannotChangeContent() throws Exception {
        Question question = Question.builder().content("가족과 가장 기억에 남는 여행은?").build();
        em.persist(question);
        Member owner = fx.member("질문엄마");
        Family family = fx.family("질문가족", owner);
        em.persist(FamilyQuestion.builder().question(question).family(family).questionDate(LocalDate.of(2026, 11, 1)).build());
        em.flush();

        mockMvc.perform(patch("/api/admin/questions/" + question.getId()).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"바꾼 문구\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN409_4"));
        mockMvc.perform(post("/api/admin/questions/" + question.getId() + "/deactivate").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.active").value(false))
                .andExpect(jsonPath("$.result.assignedCount").value(1));

        mockMvc.perform(post("/api/admin/questions").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"새 질문\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.active").value(true));
    }

    @Test
    @DisplayName("좌표가 국내 범위를 벗어나면 등록할 수 없다.")
    void createMission_invalidCoordinate() throws Exception {
        String invalid = form("좌표 오류", "[]").replace("37.5786", "0.1");
        mockMvc.perform(post("/api/admin/missions").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result[0].field").value("place.coordinate"));
    }

    private static final String STEPS = "[{\"itemType\":\"STEP\",\"title\":\"전시 둘러보기\"},{\"itemType\":\"STEP\",\"title\":\"사진 찍기\"}]";

    private Long createMission(String form) throws Exception {
        String body = mockMvc.perform(post("/api/admin/missions").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(form))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        em.flush();
        return objectMapper.readTree(body).path("result").path("missionId").asLong();
    }

    private String memberToken() {
        Member member = fx.member("피드회원");
        em.flush();
        return "Bearer " + jwtProvider.createAccessToken(member.getId());
    }

    private static String form(String title, String checklist) {
        return """
                {"title":"%s","description":"관리자가 등록한 미션","category":"EXHIBITION","missionType":"PERMANENT",
                 "price":0,"reservationUrl":"https://booking.kiri.test/1",
                 "place":{"name":"국립현대미술관","placeType":"CULTURE_FACILITY","regionCode":"11110",
                          "latitude":37.5786,"longitude":126.98},
                 "checklist":%s}
                """.formatted(title, checklist);
    }
}
