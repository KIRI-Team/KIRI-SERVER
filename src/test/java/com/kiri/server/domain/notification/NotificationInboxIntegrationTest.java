package com.kiri.server.domain.notification;

import com.kiri.server.admin.AdminFixtures;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.enums.FamilyRole;
import com.kiri.server.domain.notification.entity.Notification;
import com.kiri.server.domain.notification.enums.NotificationStatus;
import com.kiri.server.domain.notification.enums.NotificationType;
import com.kiri.server.domain.notification.repository.NotificationRepository;
import com.kiri.server.domain.notification.service.NotificationInboxService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(AdminFixtures.class)
class NotificationInboxIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AdminFixtures fx;
    @Autowired
    private EntityManager em;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private NotificationInboxService notificationInboxService;

    private Member mom;
    private Member son;

    @BeforeEach
    void setUp() {
        mom = fx.member("엄마");
        Family family = fx.family("우리집", mom);
        son = fx.join(fx.member("아들"), family, FamilyRole.SON, LocalDateTime.of(2026, 10, 2, 10, 0));
    }

    private Notification save(Member member, NotificationType type, Long referenceId, LocalDate referenceDate) {
        Notification notification = Notification.builder()
                .member(member)
                .notificationType(type)
                .title(type.name() + " 제목")
                .body("본문")
                .status(NotificationStatus.PENDING)
                .referenceId(referenceId)
                .referenceDate(referenceDate)
                .build();
        em.persist(notification);
        em.flush();
        return notification;
    }

    private void ageDays(Notification notification, int days) {
        em.createNativeQuery("UPDATE notification SET created_at = now() - make_interval(days => :days) WHERE notification_id = :id")
                .setParameter("days", days)
                .setParameter("id", notification.getId())
                .executeUpdate();
    }

    private UsernamePasswordAuthenticationToken as(Member member) {
        return new UsernamePasswordAuthenticationToken(member.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    @DisplayName("알림함은 최신순으로 보여주고 탭 분류·이동 대상·참조 날짜를 함께 내려준다")
    void listWithCategoryAndTarget() throws Exception {
        save(son, NotificationType.QUESTION_REMINDER, 1L, LocalDate.of(2026, 11, 17));
        save(son, NotificationType.SCHEDULE_INVITATION, 7L, LocalDate.of(2026, 11, 20));
        Notification poke = save(son, NotificationType.POKE, 1L, LocalDate.of(2026, 11, 17));
        save(mom, NotificationType.POKE, 1L, LocalDate.of(2026, 11, 17));
        em.clear();

        mockMvc.perform(get("/api/v1/notifications").with(authentication(as(son))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(3))
                .andExpect(jsonPath("$.result.content[0].notificationId").value(poke.getId()))
                .andExpect(jsonPath("$.result.content[0].category").value("POKE"))
                .andExpect(jsonPath("$.result.content[0].targetType").value("QUESTION"))
                .andExpect(jsonPath("$.result.content[0].referenceDate").value("2026-11-17"))
                .andExpect(jsonPath("$.result.content[1].category").value("SCHEDULE"))
                .andExpect(jsonPath("$.result.content[1].targetType").value("SCHEDULE_INVITATION"))
                .andExpect(jsonPath("$.result.content[1].referenceId").value(7));

        mockMvc.perform(get("/api/v1/notifications").param("category", "SCHEDULE").with(authentication(as(son))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.content[0].notificationType").value("SCHEDULE_INVITATION"));
    }

    @Test
    @DisplayName("없는 탭 이름으로 조회하면 400을 돌려준다")
    void invalidCategory() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").param("category", "MAIL").with(authentication(as(son))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("90일이 지난 알림은 목록과 미읽음 수에서 빠진다")
    void retentionWindow() throws Exception {
        Notification old = save(son, NotificationType.SCHEDULE_CANCELLED, 3L, LocalDate.of(2026, 8, 1));
        save(son, NotificationType.SCHEDULE_CANCELLED, 4L, LocalDate.of(2026, 11, 1));
        ageDays(old, 91);
        em.clear();

        mockMvc.perform(get("/api/v1/notifications").with(authentication(as(son))))
                .andExpect(jsonPath("$.result.totalElements").value(1));
        mockMvc.perform(get("/api/v1/notifications/unread-count").with(authentication(as(son))))
                .andExpect(jsonPath("$.result.unreadCount").value(1))
                .andExpect(jsonPath("$.result.badge").value("1"));
    }

    @Test
    @DisplayName("보관기간 정리 작업은 90일이 지난 알림만 지운다")
    void deleteExpired() {
        Notification old = save(son, NotificationType.SCHEDULE_CANCELLED, 3L, LocalDate.of(2026, 8, 1));
        Notification recent = save(son, NotificationType.SCHEDULE_CANCELLED, 4L, LocalDate.of(2026, 11, 1));
        ageDays(old, 91);

        int deleted = notificationInboxService.deleteExpiredBatch(1000);

        assertThat(deleted).isEqualTo(1);
        assertThat(notificationRepository.findById(old.getId())).isEmpty();
        assertThat(notificationRepository.findById(recent.getId())).isPresent();
    }

    @Test
    @DisplayName("모두 읽음은 탭과 관계없이 본인의 미읽음 알림만 읽음 처리한다")
    void readAll() throws Exception {
        save(son, NotificationType.QUESTION_REMINDER, 1L, LocalDate.of(2026, 11, 17));
        save(son, NotificationType.SCHEDULE_INVITATION, 7L, LocalDate.of(2026, 11, 20));
        Notification momPoke = save(mom, NotificationType.POKE, 1L, LocalDate.of(2026, 11, 17));
        em.clear();

        mockMvc.perform(patch("/api/v1/notifications/read-all").with(authentication(as(son))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.readCount").value(2));
        mockMvc.perform(get("/api/v1/notifications/unread-count").with(authentication(as(son))))
                .andExpect(jsonPath("$.result.unreadCount").value(0))
                .andExpect(jsonPath("$.result.badge").doesNotExist());

        assertThat(notificationRepository.findById(momPoke.getId()).orElseThrow().getIsRead()).isFalse();
    }

    @Test
    @DisplayName("다른 사람의 알림은 읽음 처리할 수 없다")
    void cannotReadOthers() throws Exception {
        Notification momPoke = save(mom, NotificationType.POKE, 1L, LocalDate.of(2026, 11, 17));

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", momPoke.getId()).with(authentication(as(son))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("같은 일정의 응답·변경 알림은 같은 날짜여도 여러 건 저장된다")
    void scheduleNotificationsOnSameDateAreNotBlocked() {
        LocalDate date = LocalDate.of(2026, 11, 20);
        save(mom, NotificationType.SCHEDULE_RESPONSE, 7L, date);
        save(mom, NotificationType.SCHEDULE_RESPONSE, 7L, date);
        save(mom, NotificationType.SCHEDULE_D1_REMINDER, 8L, date);
        save(mom, NotificationType.SCHEDULE_D1_REMINDER, 9L, date);

        assertThat(notificationRepository.count()).isGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("질문 미응답 알림은 여전히 날짜당 1건만 저장된다")
    void questionReminderStillUniquePerDate() {
        LocalDate date = LocalDate.of(2026, 11, 17);
        save(son, NotificationType.QUESTION_REMINDER, 1L, date);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> save(son, NotificationType.QUESTION_REMINDER, 1L, date))
                .isInstanceOf(Exception.class);
    }
}
