package com.kiri.server.admin.review;

import com.kiri.server.admin.AdminFixtures;
import com.kiri.server.admin.AdminTestSupport;
import com.kiri.server.admin.audit.enums.AuditAction;
import com.kiri.server.admin.audit.enums.AuditTargetType;
import com.kiri.server.admin.audit.repository.AdminAuditLogRepository;
import com.kiri.server.admin.review.entity.MissionReviewTask;
import com.kiri.server.admin.review.enums.ReviewTaskStatus;
import com.kiri.server.admin.review.repository.MissionReviewTaskRepository;
import com.kiri.server.domain.external.client.tourapi.dto.TourApiResDTO;
import com.kiri.server.domain.external.entity.ExternalRecord;
import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.enums.ProcessStatus;
import com.kiri.server.domain.external.enums.ReviewReason;
import com.kiri.server.domain.external.enums.SyncDecision;
import com.kiri.server.domain.external.service.ExternalRecordSyncService;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.LockedField;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.mission.generator.dto.DraftResult;
import com.kiri.server.domain.mission.generator.dto.MissionDraft;
import com.kiri.server.domain.mission.service.MissionGenerationService;
import com.kiri.server.domain.place.entity.Place;
import com.kiri.server.domain.mission.enums.Category;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({AdminTestSupport.class, AdminFixtures.class})
class AdminReviewIntegrationTest {

    private static final String RAW = "{\"item\":{\"contentid\":\"%s\",\"contenttypeid\":\"12\",\"title\":\"%s\"},\"common\":null,\"intro\":null,\"info\":[]}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AdminTestSupport support;
    @Autowired
    private AdminFixtures fx;
    @Autowired
    private EntityManager em;
    @Autowired
    private MissionReviewTaskRepository taskRepository;
    @Autowired
    private AdminAuditLogRepository auditLogRepository;
    @Autowired
    private ExternalRecordSyncService externalRecordSyncService;
    @Autowired
    private MissionGenerationService missionGenerationService;

    private String token;

    @BeforeEach
    void setUp() {
        token = support.bearer(support.createAdmin("review-admin"));
    }

    @Test
    @DisplayName("[탭①] 검증 실패 원본만 대기열에 나오고 사유로 거를 수 있다.")
    void queue_filters() throws Exception {
        ExternalRecord noLocation = record("rv-1", "좌표 없는 전시", null);
        noLocation.markReviewRequired(ReviewReason.NO_LOCATION);
        ExternalRecord unknown = record("rv-2", "분류 모호 행사", null);
        unknown.markReviewRequired(ReviewReason.CATEGORY_UNKNOWN);
        record("rv-3", "정상 처리", null);
        em.flush();

        mockMvc.perform(get("/api/admin/reviews").param("reason", "NO_LOCATION").param("keyword", "좌표 없는")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.content[0].externalRecordId").value(noLocation.getId()))
                .andExpect(jsonPath("$.result.content[0].originTitle").value("좌표 없는 전시"))
                .andExpect(jsonPath("$.result.content[0].taskStatus").value("OPEN"))
                .andExpect(jsonPath("$.result.content[0].previouslyApproved").value(false));
    }

    @Test
    @DisplayName("[탭①] 승인하면 새 미션을 공개하고 원본을 처리 완료로 바꾸며 작업·이력을 남긴다.")
    void approve_createsMission() throws Exception {
        ExternalRecord record = record("rv-10", "좌표 없는 전시", null);
        record.markReviewRequired(ReviewReason.NO_LOCATION);
        em.flush();

        mockMvc.perform(post("/api/admin/reviews/" + record.getId() + "/approve").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(approveBody("국립현대미술관 관람하기", "PERMANENT", null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.missionStatus").value("ACTIVE"));
        em.flush();
        em.clear();

        ExternalRecord saved = em.find(ExternalRecord.class, record.getId());
        assertThat(saved.getProcessStatus()).isEqualTo(ProcessStatus.PROCESSED);
        Mission mission = saved.getMission();
        assertThat(mission.getTitle()).isEqualTo("국립현대미술관 관람하기");
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.ACTIVE);
        assertThat(mission.isLocked(LockedField.TITLE)).isTrue();
        assertThat(taskRepository.findAllByExternalRecordIdOrderByIdDesc(record.getId()))
                .singleElement().satisfies(task -> {
                    assertThat(task.getStatus()).isEqualTo(ReviewTaskStatus.APPROVED);
                    assertThat(task.getAppliedOverrides()).contains("국립현대미술관 관람하기");
                });
        assertThat(auditLogRepository.findAllByTargetTypeAndTargetIdOrderByIdDesc(AuditTargetType.EXTERNAL_RECORD, record.getId()))
                .extracting("action").containsExactly(AuditAction.APPROVE);
    }

    @Test
    @DisplayName("[탭①] 공개 조건을 통과하지 못하면 필드별 오류와 함께 400이고 아무것도 바뀌지 않는다.")
    void approve_validationFailed() throws Exception {
        ExternalRecord record = record("rv-11", "기간 없는 행사", null);
        record.markReviewRequired(ReviewReason.MISSING_REQUIRED);
        em.flush();

        mockMvc.perform(post("/api/admin/reviews/" + record.getId() + "/approve").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(approveBody("행사 방문하기", "PERIOD", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN400_3"))
                .andExpect(jsonPath("$.result[0].field").value("period"));

        mockMvc.perform(post("/api/admin/reviews/" + record.getId() + "/revalidate").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(formJson("행사 방문하기", "PERIOD", "2026-11-01", "2026-11-30")))
                .andExpect(jsonPath("$.result.valid").value(true));
        em.flush();
        em.clear();
        assertThat(em.find(ExternalRecord.class, record.getId()).getProcessStatus()).isEqualTo(ProcessStatus.REVIEW_REQUIRED);
    }

    @Test
    @DisplayName("[탭①] 확인 필요 미션을 승인하면 공개하되, 기간이 이미 지났으면 바로 종료한다.")
    void approve_existingCheckRequired_periodOver() throws Exception {
        Mission mission = fx.mission(fx.place("성수"), "지난 팝업", MissionStatus.CHECK_REQUIRED);
        ExternalRecord record = record("rv-12", "지난 팝업", mission);
        record.markReviewRequired(ReviewReason.DUPLICATE_SUSPECT);
        em.flush();

        mockMvc.perform(post("/api/admin/reviews/" + record.getId() + "/approve").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(approveBody("지난 팝업", "PERIOD", "2020-01-01", "2020-01-31")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.missionId").value(mission.getId()))
                .andExpect(jsonPath("$.result.missionStatus").value("ENDED"));
    }

    @Test
    @DisplayName("[탭①] 종료·숨김 미션은 승인해도 상태를 바꾸지 않는다.")
    void approve_keepsEndedAndHidden() throws Exception {
        for (MissionStatus kept : new MissionStatus[]{MissionStatus.ENDED, MissionStatus.HIDDEN, MissionStatus.CANCELLED}) {
            Mission mission = fx.mission(fx.place("장소-" + kept), "미션-" + kept, kept);
            ExternalRecord record = record("rv-keep-" + kept, "미션-" + kept, mission);
            record.markReviewRequired(ReviewReason.CATEGORY_UNKNOWN);
            em.flush();

            mockMvc.perform(post("/api/admin/reviews/" + record.getId() + "/approve").header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON).content(approveBody("미션-" + kept, "PERMANENT", null, null)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.missionStatus").value(kept.name()));
        }
    }

    @Test
    @DisplayName("[탭①] 제외하면 원본은 ADMIN_EXCLUDED로 무시되고 미션은 숨겨지며, 원본이 바뀐 재수집에서도 건너뛴다.")
    void reject_staysIgnoredOnResync() throws Exception {
        Mission mission = fx.mission(fx.place("광화문"), "중복 의심 전시", MissionStatus.CHECK_REQUIRED);
        ExternalRecord record = record("rv-20", "중복 의심 전시", mission);
        record.markReviewRequired(ReviewReason.DUPLICATE_SUSPECT);
        em.flush();

        mockMvc.perform(post("/api/admin/reviews/" + record.getId() + "/reject").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN400_1"));
        mockMvc.perform(post("/api/admin/reviews/" + record.getId() + "/reject").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"기존 미션과 중복\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("REJECTED"));
        em.flush();
        em.clear();

        ExternalRecord saved = em.find(ExternalRecord.class, record.getId());
        assertThat(saved.getProcessStatus()).isEqualTo(ProcessStatus.IGNORED);
        assertThat(saved.getReviewReason()).isEqualTo(ReviewReason.ADMIN_EXCLUDED);
        assertThat(em.find(Mission.class, mission.getId()).getStatus()).isEqualTo(MissionStatus.HIDDEN);

        TourApiResDTO.SyncItem changed = TourApiResDTO.SyncItem.builder().contentId("rv-20").contentTypeId("12")
                .title("중복 의심 전시 (수정)").shown(true).modifiedAt(LocalDateTime.of(2030, 1, 1, 0, 0)).build();
        assertThat(externalRecordSyncService.decide(changed)).isEqualTo(SyncDecision.UNCHANGED);

        mockMvc.perform(post("/api/admin/reviews/" + record.getId() + "/approve").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(approveBody("중복 의심 전시", "PERMANENT", null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN409_5"));
    }

    @Test
    @DisplayName("[탭①] 보류하면 업무 상태만 바뀌고 보류 필터로 찾을 수 있다.")
    void hold() throws Exception {
        ExternalRecord record = record("rv-30", "업종 확인 필요", null);
        record.markReviewRequired(ReviewReason.PLACE_AMBIGUOUS);
        em.flush();

        mockMvc.perform(post("/api/admin/reviews/" + record.getId() + "/hold").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"업체 확인 중\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("ON_HOLD"));

        mockMvc.perform(get("/api/admin/reviews").param("taskStatus", "ON_HOLD").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.content[0].externalRecordId").value(record.getId()));
        assertThat(em.find(ExternalRecord.class, record.getId()).getProcessStatus()).isEqualTo(ProcessStatus.REVIEW_REQUIRED);
    }

    @Test
    @DisplayName("[탭①] 승인한 원본이 다시 검수로 들어오면 '이전에 승인됨'과 이전 보완 값을 보여준다.")
    void reentered_showsPreviousOverrides() throws Exception {
        ExternalRecord record = record("rv-40", "좌표 없는 공연", null);
        record.markReviewRequired(ReviewReason.NO_LOCATION);
        em.flush();
        mockMvc.perform(post("/api/admin/reviews/" + record.getId() + "/approve").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(approveBody("운영자가 고친 제목", "PERMANENT", null, null)))
                .andExpect(status().isOk());
        em.flush();
        em.find(ExternalRecord.class, record.getId()).markReviewRequired(ReviewReason.NO_LOCATION);
        em.flush();

        mockMvc.perform(get("/api/admin/reviews").param("keyword", "좌표 없는 공연").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.result.content[0].previouslyApproved").value(true));
        mockMvc.perform(get("/api/admin/reviews/" + record.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.previouslyApproved").value(true))
                .andExpect(jsonPath("$.result.previousOverrides.title").value("운영자가 고친 제목"))
                .andExpect(jsonPath("$.result.currentMission.lockedFields[0]").exists())
                .andExpect(jsonPath("$.result.origin.item.title").value("좌표 없는 공연"));
    }

    @Test
    @DisplayName("[탭②] 처리 완료 원본의 확인 필요 미션만 공개 대기로 나오고, 켜면 공개·끄면 숨긴다.")
    void pendingPublish_publishAndHide() throws Exception {
        Mission toPublish = fx.mission(fx.place("서울숲"), "공개 대기 A", MissionStatus.CHECK_REQUIRED);
        record("pp-1", "공개 대기 A", toPublish).markProcessed(toPublish);
        Mission toHide = fx.mission(fx.place("남산"), "공개 대기 B", MissionStatus.CHECK_REQUIRED);
        ExternalRecord hideRecord = record("pp-2", "공개 대기 B", toHide);
        hideRecord.markProcessed(toHide);
        Mission failed = fx.mission(fx.place("한강"), "검증 실패 미션", MissionStatus.CHECK_REQUIRED);
        record("pp-3", "검증 실패 미션", failed).markReviewRequired(ReviewReason.NO_LOCATION);
        em.flush();

        mockMvc.perform(get("/api/admin/reviews/pending-publish").param("keyword", "공개 대기").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.result.totalElements").value(2));
        mockMvc.perform(get("/api/admin/reviews/pending-publish").param("keyword", "검증 실패 미션").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.result.totalElements").value(0));

        mockMvc.perform(post("/api/admin/reviews/pending-publish/" + toPublish.getId() + "/publish").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("ACTIVE"));
        mockMvc.perform(post("/api/admin/reviews/pending-publish/" + toHide.getId() + "/hide").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"품질 미달\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("HIDDEN"));
        mockMvc.perform(post("/api/admin/reviews/pending-publish/" + toPublish.getId() + "/publish").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isConflict());
        em.flush();

        // 숨긴 미션은 재수집(검증 통과)으로 다시 노출되지 않는다.
        Place place = em.find(Mission.class, toHide.getId()).getPlace();
        MissionDraft draft = MissionDraft.builder().title("공개 대기 B").description("설명")
                .category(Category.EXHIBITION).missionType(MissionType.PERMANENT)
                .place(MissionDraft.PlaceDraft.builder().name(place.getName()).placeType(PlaceType.CULTURE_FACILITY)
                        .latitude(place.getLatitude()).longitude(place.getLongitude()).build())
                .build();
        missionGenerationService.apply(em.find(ExternalRecord.class, hideRecord.getId()), new DraftResult.Ready(draft), LocalDate.now());
        em.flush();
        em.clear();
        assertThat(em.find(Mission.class, toHide.getId()).getStatus()).isEqualTo(MissionStatus.HIDDEN);
    }

    @Test
    @DisplayName("[탭②] 일괄 처리 중 하나라도 공개 대기가 아니면 전체를 취소한다.")
    void bulk_rollsBackAll() throws Exception {
        Mission a = fx.mission(fx.place("A"), "일괄 A", MissionStatus.CHECK_REQUIRED);
        record("bk-1", "일괄 A", a).markProcessed(a);
        Mission active = fx.mission(fx.place("B"), "이미 공개", MissionStatus.ACTIVE);
        em.flush();

        mockMvc.perform(post("/api/admin/reviews/pending-publish/bulk").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"missionIds\":[" + a.getId() + "," + active.getId() + "],\"action\":\"PUBLISH\"}"))
                .andExpect(status().isConflict());
    }

    private ExternalRecord record(String externalId, String title, Mission mission) {
        ExternalRecord record = ExternalRecord.builder()
                .externalSource(ExternalSource.TOUR_API)
                .externalId(externalId)
                .mission(mission)
                .place(mission == null ? null : mission.getPlace())
                .processStatus(ProcessStatus.PROCESSED)
                .rawData(RAW.formatted(externalId, title))
                .contentHash("hash-" + externalId)
                .syncedAt(LocalDateTime.now())
                .build();
        em.persist(record);
        return record;
    }

    private static String approveBody(String title, String missionType, String start, String end) {
        return "{\"note\":\"검수 메모\",\"form\":" + formJson(title, missionType, start, end) + "}";
    }

    private static String formJson(String title, String missionType, String start, String end) {
        return """
                {"title":"%s","description":"소개","category":"EXHIBITION","missionType":"%s",
                 "startDate":%s,"endDate":%s,
                 "place":{"name":"국립현대미술관","placeType":"CULTURE_FACILITY","regionCode":"11110",
                          "latitude":37.5786,"longitude":126.98},
                 "checklist":[{"itemType":"STEP","title":"전시 둘러보기"},{"itemType":"STEP","title":"기념 사진 찍기"}]}
                """.formatted(title, missionType, start == null ? "null" : "\"" + start + "\"",
                end == null ? "null" : "\"" + end + "\"");
    }
}
