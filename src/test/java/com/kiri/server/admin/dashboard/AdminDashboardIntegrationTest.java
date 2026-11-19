package com.kiri.server.admin.dashboard;

import com.kiri.server.admin.AdminFixtures;
import com.kiri.server.admin.AdminTestSupport;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({AdminTestSupport.class, AdminFixtures.class})
class AdminDashboardIntegrationTest {

    // 관찰 기간(28일)이 충분히 지난 주
    private static final LocalDate WEEK = LocalDate.now().minusWeeks(7).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    private static final LocalDateTime IN_WEEK = WEEK.plusDays(2).atTime(15, 0);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AdminTestSupport support;
    @Autowired
    private AdminFixtures fx;
    @Autowired
    private EntityManager em;

    private String token;
    private Mission mission;

    @BeforeEach
    void setUp() {
        token = support.bearer(support.createAdmin("dash-admin"));
        mission = fx.mission(fx.place("대시보드 장소"), "대시보드 미션", MissionStatus.ACTIVE);
    }

    @Test
    @DisplayName("완료 가족·완료 일정·다음 활동률을 계산하고 테스트 가족은 뺀다.")
    void completionKpis() throws Exception {
        Family a = family("A");
        completed(a, IN_WEEK);
        completed(a, IN_WEEK.plusDays(10));      // 28일 안에 다음 활동
        Family b = family("B");
        completed(b, IN_WEEK.plusHours(1));       // 다음 활동 없음
        Family testFamily = family("T");
        completed(testFamily, IN_WEEK);
        testFamily.markTest(true);
        em.flush();

        mockMvc.perform(get("/api/admin/dashboard/kpis").param("weekOf", WEEK.toString()).param("fresh", "true")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result[?(@.key=='WEEKLY_COMPLETED_FAMILIES')].value").value(2.0))
                .andExpect(jsonPath("$.result[?(@.key=='WEEKLY_COMPLETED_SCHEDULES')].value").value(2.0))
                .andExpect(jsonPath("$.result[?(@.key=='WEEKLY_COMPLETED_SCHEDULES')].previousValue").value(0.0))
                .andExpect(jsonPath("$.result[?(@.key=='NEXT_ACTIVITY_RATE')].value").value(0.5))
                .andExpect(jsonPath("$.result[?(@.key=='NEXT_ACTIVITY_RATE')].numerator").value(1))
                .andExpect(jsonPath("$.result[?(@.key=='NEXT_ACTIVITY_RATE')].denominator").value(2))
                .andExpect(jsonPath("$.result[?(@.key=='RELATIONSHIP_POSITIVE_RATE')].dataStatus").value("NOT_COLLECTED"))
                .andExpect(jsonPath("$.result[?(@.key=='WEEKLY_COMPLETED_FAMILIES')].trend.length()").value(8));
    }

    @Test
    @DisplayName("일정 확정률은 응답 마감 전 일정을 빼고, 활동 완료율은 확정 일정 중 완료 비율이다.")
    void rateKpis() throws Exception {
        Family f = family("R");
        Member proposer = f.getOwner();
        BucketItem bucket = fx.bucket(f, proposer, mission, BucketStatus.SAVED);
        Schedule confirmed = fx.schedule(f, bucket, proposer, WEEK.plusDays(3), ScheduleStatus.CONFIRMED, null);
        Schedule notConfirmed = fx.schedule(f, bucket, proposer, WEEK.plusDays(3), ScheduleStatus.NOT_CONFIRMED, null);
        Schedule pendingOpen = fx.schedule(f, bucket, proposer, WEEK.plusDays(3), ScheduleStatus.PENDING_RESPONSE, null);
        Schedule done = fx.schedule(f, bucket, proposer, WEEK.plusDays(4), ScheduleStatus.COMPLETED, IN_WEEK.plusDays(2));
        em.flush();
        for (Schedule s : new Schedule[]{confirmed, notConfirmed, pendingOpen, done}) {
            setCreatedAt("schedule", "schedule_id", s.getId(), IN_WEEK);
        }
        em.createNativeQuery("UPDATE schedule SET response_deadline = :d WHERE schedule_id = :id")
                .setParameter("d", LocalDateTime.now().plusDays(1)).setParameter("id", pendingOpen.getId()).executeUpdate();

        mockMvc.perform(get("/api/admin/dashboard/kpis").param("weekOf", WEEK.toString()).param("fresh", "true")
                        .header(HttpHeaders.AUTHORIZATION, token))
                // 확정률: 분모 3(응답 대기 제외), 분자 2(CONFIRMED, COMPLETED)
                .andExpect(jsonPath("$.result[?(@.key=='SCHEDULE_CONFIRMATION_RATE')].numerator").value(2))
                .andExpect(jsonPath("$.result[?(@.key=='SCHEDULE_CONFIRMATION_RATE')].denominator").value(3))
                .andExpect(jsonPath("$.result[?(@.key=='SCHEDULE_CONFIRMATION_RATE')].value").value(0.6667))
                // 완료율: 확정 2건 중 완료 1건
                .andExpect(jsonPath("$.result[?(@.key=='ACTIVITY_COMPLETION_RATE')].value").value(0.5))
                .andExpect(jsonPath("$.result[?(@.key=='WEEKLY_ACTIVE_FAMILIES')].value").value(1.0));
    }

    @Test
    @DisplayName("두 번째 구성원이 그 주에 가입한 가족을 신규 연결 가족으로 센다.")
    void newConnectedFamilies() throws Exception {
        Family f = family("N");
        fx.join(f.getOwner(), f, FamilyRole.MOTHER, WEEK.minusDays(10).atStartOfDay());
        fx.join(fx.member("둘째"), f, FamilyRole.SON, IN_WEEK);
        Family alone = family("S");
        em.flush();

        mockMvc.perform(get("/api/admin/dashboard/kpis/NEW_CONNECTED_FAMILIES").param("weekOf", WEEK.toString())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.kpi.value").value(1.0))
                .andExpect(jsonPath("$.result.rows[0].id").value(f.getId()));
    }

    @Test
    @DisplayName("다음 활동률은 28일이 지나지 않은 주를 관찰 기간 미충족으로 표시한다.")
    void nextActivity_insufficientPeriod() throws Exception {
        LocalDate thisWeek = LocalDate.now();
        mockMvc.perform(get("/api/admin/dashboard/kpis").param("weekOf", thisWeek.toString()).param("fresh", "true")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.result[?(@.key=='NEXT_ACTIVITY_RATE')].dataStatus").value("INSUFFICIENT_PERIOD"))
                .andExpect(jsonPath("$.result[?(@.key=='WEEKLY_COMPLETED_FAMILIES')].dataStatus").value("OK"));
    }

    @Test
    @DisplayName("완료 일정 상세는 리뷰 쓴 참여자 / 수락 참여자 분포를 준다.")
    void completedDetail_breakdown() throws Exception {
        Family f = family("D");
        Member son = fx.join(fx.member("아들"), f, FamilyRole.SON, LocalDateTime.of(2026, 1, 1, 0, 0));
        BucketItem bucket = fx.bucket(f, f.getOwner(), mission, BucketStatus.COMPLETED);
        Schedule s = fx.schedule(f, bucket, f.getOwner(), WEEK.plusDays(1), ScheduleStatus.COMPLETED, IN_WEEK);
        ScheduleParticipant p1 = fx.participant(s, f.getOwner(), ParticipantStatus.ACCEPTED);
        fx.participant(s, son, ParticipantStatus.ACCEPTED);
        fx.review(s, p1, 5, true);
        em.flush();

        mockMvc.perform(get("/api/admin/dashboard/kpis/WEEKLY_COMPLETED_SCHEDULES").param("weekOf", WEEK.toString())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.result.breakdown[0].label").value("리뷰 1명 / 수락 2명"))
                .andExpect(jsonPath("$.result.breakdown[0].count").value(1));
    }

    @Test
    @DisplayName("운영 주의 항목을 집계한다.")
    void ops() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/ops").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.reviewQueueCount").isNumber())
                .andExpect(jsonPath("$.result.pendingPublishCount").isNumber());
    }

    private Family family(String name) {
        Member owner = fx.member("대표" + name);
        return fx.family("대시" + name, owner);
    }

    private void completed(Family f, LocalDateTime completedAt) {
        BucketItem bucket = fx.bucket(f, f.getOwner(), mission, BucketStatus.COMPLETED);
        fx.schedule(f, bucket, f.getOwner(), completedAt.toLocalDate(), ScheduleStatus.COMPLETED, completedAt);
    }

    private void setCreatedAt(String table, String idColumn, Long id, LocalDateTime at) {
        em.createNativeQuery("UPDATE " + table + " SET created_at = :at WHERE " + idColumn + " = :id")
                .setParameter("at", at).setParameter("id", id).executeUpdate();
    }
}
