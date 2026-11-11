package com.kiri.server.admin.lookup;

import com.kiri.server.admin.AdminFixtures;
import com.kiri.server.admin.AdminTestSupport;
import com.kiri.server.admin.audit.enums.AuditAction;
import com.kiri.server.admin.audit.enums.AuditTargetType;
import com.kiri.server.admin.audit.repository.AdminAuditLogRepository;
import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.bucket.enums.BucketStatus;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.enums.FamilyRole;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({AdminTestSupport.class, AdminFixtures.class})
class AdminLookupIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AdminTestSupport support;
    @Autowired
    private AdminFixtures fx;
    @Autowired
    private AdminAuditLogRepository auditLogRepository;
    @Autowired
    private EntityManager em;

    private String token;
    private Member mom;
    private Member son;
    private Family family;
    private Schedule schedule;

    @BeforeEach
    void setUp() {
        token = support.bearer(support.createAdmin("lookup-admin"));
        mom = fx.member("엄마");
        family = fx.family("우리집", mom);
        son = fx.join(fx.member("아들"), family, FamilyRole.SON, LocalDateTime.of(2026, 10, 2, 10, 0));
        Mission mission = fx.mission(fx.place("서울숲"), "서울숲 산책하기", MissionStatus.ACTIVE);
        BucketItem bucket = fx.bucket(family, mom, mission, BucketStatus.SCHEDULED);
        schedule = fx.schedule(family, bucket, mom, LocalDate.of(2026, 10, 10), ScheduleStatus.CONFIRMED, null);
        fx.participant(schedule, mom, ParticipantStatus.ACCEPTED);
        ScheduleParticipant sonParticipant = fx.participant(schedule, son, ParticipantStatus.ACCEPTED);
        fx.review(schedule, sonParticipant, 5, true);
        fx.flushAndClear();
    }

    @Test
    @DisplayName("닉네임 키워드로 회원을 검색하면 가족 정보와 함께 돌려준다.")
    void searchMembers_byKeyword() throws Exception {
        mockMvc.perform(get("/api/admin/members").param("keyword", son.getNickname()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.content[0].memberId").value(son.getId()))
                .andExpect(jsonPath("$.result.content[0].familyId").value(family.getId()))
                .andExpect(jsonPath("$.result.content[0].familyRole").value("SON"));
    }

    @Test
    @DisplayName("탈퇴한 회원은 회원 ID와 WITHDRAWN 상태로 찾는다.")
    void searchMembers_withdrawn() throws Exception {
        Member withdrawn = em.find(Member.class, son.getId());
        withdrawn.withdraw();
        em.flush();

        mockMvc.perform(get("/api/admin/members").param("memberId", son.getId().toString()).param("state", "WITHDRAWN")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.content[0].withdrawn").value(true))
                .andExpect(jsonPath("$.result.content[0].nickname").doesNotExist());
    }

    @Test
    @DisplayName("회원 상세는 활동 요약을 계산한다.")
    void getMember_activity() throws Exception {
        mockMvc.perform(get("/api/admin/members/" + mom.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.activity.bucketCount").value(1))
                .andExpect(jsonPath("$.result.activity.proposedScheduleCount").value(1))
                .andExpect(jsonPath("$.result.activity.participatedScheduleCount").value(1))
                .andExpect(jsonPath("$.result.activity.reviewCount").value(0))
                .andExpect(jsonPath("$.result.tasteConfigured").value(false));
    }

    @Test
    @DisplayName("가족 상세는 구성원과 상태별 집계를 주고 상세 주소는 주지 않는다.")
    void getFamily_detail() throws Exception {
        mockMvc.perform(get("/api/admin/families/" + family.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.family.memberCount").value(2))
                .andExpect(jsonPath("$.result.members[0].owner").value(true))
                .andExpect(jsonPath("$.result.bucketCounts.SCHEDULED").value(1))
                .andExpect(jsonPath("$.result.scheduleCounts.CONFIRMED").value(1))
                .andExpect(jsonPath("$.result.reviewCount").value(1))
                .andExpect(jsonPath("$.result.address").doesNotExist());
    }

    @Test
    @DisplayName("리뷰가 있는데 완료되지 않은 일정은 불일치 경고를 준다.")
    void getSchedule_warning() throws Exception {
        mockMvc.perform(get("/api/admin/schedules/" + schedule.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.participants.length()").value(2))
                .andExpect(jsonPath("$.result.reviews[0].starRating").value(5))
                .andExpect(jsonPath("$.result.reviews[0].content").doesNotExist())
                .andExpect(jsonPath("$.result.warnings", hasItem("REVIEW_WITHOUT_COMPLETION")));
    }

    @Test
    @DisplayName("가족 일정 목록을 상태로 거른다.")
    void searchSchedules_byStatus() throws Exception {
        mockMvc.perform(get("/api/admin/families/" + family.getId() + "/schedules").param("status", "CONFIRMED")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.content[0].missionTitle").value("서울숲 산책하기"));
        mockMvc.perform(get("/api/admin/families/" + family.getId() + "/schedules").param("status", "COMPLETED")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.result.totalElements").value(0));
    }

    @Test
    @DisplayName("테스트 가족으로 지정하면 저장되고 변경 이력이 남는다.")
    void markFamilyTest() throws Exception {
        mockMvc.perform(patch("/api/admin/families/" + family.getId() + "/test-flag").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"isTest\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isTest").value(true));
        em.flush();
        em.clear();
        assertThat(em.find(Family.class, family.getId()).getIsTest()).isTrue();
        assertThat(auditLogRepository.findAllByTargetTypeAndTargetIdOrderByIdDesc(AuditTargetType.FAMILY, family.getId()))
                .extracting("action").containsExactly(AuditAction.UPDATE);
    }

    @Test
    @DisplayName("민감정보 열람은 사유가 없으면 400이고, 있으면 리뷰 본문을 주고 열람 이력을 남긴다.")
    void viewSensitive() throws Exception {
        mockMvc.perform(post("/api/admin/families/" + family.getId() + "/sensitive").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN400_1"));

        mockMvc.perform(post("/api/admin/families/" + family.getId() + "/sensitive").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"리뷰 누락 문의 대응\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.reviews[0].content").value("좋았어요"));
        assertThat(auditLogRepository.findAllByTargetTypeAndTargetIdOrderByIdDesc(AuditTargetType.FAMILY, family.getId()))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getAction()).isEqualTo(AuditAction.VIEW_SENSITIVE);
                    assertThat(log.getReason()).isEqualTo("리뷰 누락 문의 대응");
                });
    }

    @Test
    @DisplayName("없는 가족은 404이다.")
    void getFamily_notFound() throws Exception {
        mockMvc.perform(get("/api/admin/families/999999999").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADMIN404_2"));
    }
}
