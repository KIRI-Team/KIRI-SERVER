package com.kiri.server.admin.dashboard.service;

import com.kiri.server.admin.dashboard.dto.DashboardDTO.KpiKey;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// KPI 산식. 모두 KST 기준 [start, end) 한 주를 받는다. 테스트 가족(family.is_test)은 제외한다.
@Component
@RequiredArgsConstructor
public class KpiCalculator {

    // 리뷰 작성 기한이 없으므로, 일정일로부터 이 기간이 지난 확정 일정만 활동 완료율 분모에 넣는다.
    public static final int COMPLETION_GRACE_DAYS = 7;
    public static final int NEXT_ACTIVITY_DAYS = 28;

    private final EntityManager em;

    // 분자, 분모(비율이 아니면 null), 관찰 기간 충족 여부
    public record Measure(long numerator, Long denominator, boolean observable) {
    }

    public static final Map<KpiKey, String> FORMULAS = Map.of(
            KpiKey.WEEKLY_COMPLETED_FAMILIES, "그 주(KST 월~일)에 completed_at이 있는 COMPLETED 일정을 가진 가족 수. 완료는 참여자 1명의 유효 리뷰 기준이며 가족 전원 참여·만족을 뜻하지 않습니다.",
            KpiKey.WEEKLY_COMPLETED_SCHEDULES, "그 주에 completed_at이 있는 COMPLETED 일정 수. 완료 = 참여자 1명의 유효 리뷰 기준입니다.",
            KpiKey.NEW_CONNECTED_FAMILIES, "두 번째 구성원의 가입 시각(family_joined_at)이 그 주에 속하는 가족 수. 현재 가족에 남아 있는 구성원 기준입니다.",
            KpiKey.WEEKLY_ACTIVE_FAMILIES, "그 주에 질문 답변·버킷 저장·일정 생성·초대 응답·리뷰 작성 중 하나라도 있는 가족 수",
            KpiKey.SCHEDULE_CONFIRMATION_RATE, "그 주에 생성된 일정 중 CONFIRMED 또는 COMPLETED에 도달한 비율. 응답 마감 전인 응답 대기 일정은 분모에서 제외",
            KpiKey.ACTIVITY_COMPLETION_RATE, "일정일이 그 주인 확정(CONFIRMED·COMPLETED) 일정 중 COMPLETED 비율. 일정일로부터 " + COMPLETION_GRACE_DAYS + "일이 지나지 않은 일정은 분모에서 제외",
            KpiKey.NEXT_ACTIVITY_RATE, "그 주에 첫 활동을 완료한 가족 중 " + NEXT_ACTIVITY_DAYS + "일 안에 다음 활동을 완료한 비율. 첫 완료 후 " + NEXT_ACTIVITY_DAYS + "일이 지나지 않은 주는 관찰 기간 미충족",
            KpiKey.RELATIONSHIP_POSITIVE_RATE, "활동을 통해 서로를 새롭게 알게 됐다는 응답 비율. 경험 설문이 아직 없어 미수집입니다.");

    public Measure measure(KpiKey key, LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        return switch (key) {
            case WEEKLY_COMPLETED_FAMILIES -> count("""
                    SELECT COUNT(DISTINCT s.family_id) FROM schedule s JOIN family f ON f.family_id = s.family_id
                     WHERE f.is_test = FALSE AND s.status = 'COMPLETED' AND s.completed_at >= :start AND s.completed_at < :end
                    """, start, end);
            case WEEKLY_COMPLETED_SCHEDULES -> count("""
                    SELECT COUNT(*) FROM schedule s JOIN family f ON f.family_id = s.family_id
                     WHERE f.is_test = FALSE AND s.status = 'COMPLETED' AND s.completed_at >= :start AND s.completed_at < :end
                    """, start, end);
            case NEW_CONNECTED_FAMILIES -> count("""
                    SELECT COUNT(*) FROM (
                        SELECT (array_agg(m.family_joined_at ORDER BY m.family_joined_at))[2] AS second_joined
                          FROM member m JOIN family f ON f.family_id = m.family_id
                         WHERE f.is_test = FALSE AND m.family_joined_at IS NOT NULL
                         GROUP BY m.family_id) t
                     WHERE t.second_joined >= :start AND t.second_joined < :end
                    """, start, end);
            case WEEKLY_ACTIVE_FAMILIES -> count("SELECT COUNT(DISTINCT a.family_id) FROM (" + ACTIVE_FAMILIES + ") a", start, end);
            case SCHEDULE_CONFIRMATION_RATE -> confirmationRate(start, end, now);
            case ACTIVITY_COMPLETION_RATE -> completionRate(start, end, now);
            case NEXT_ACTIVITY_RATE -> nextActivityRate(start, end, now);
            case RELATIONSHIP_POSITIVE_RATE -> null;
        };
    }

    // 상세 화면: 분자에 들어간 대상
    @SuppressWarnings("unchecked")
    public List<Object[]> numeratorRows(KpiKey key, LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        String sql = switch (key) {
            case WEEKLY_COMPLETED_FAMILIES, WEEKLY_COMPLETED_SCHEDULES -> """
                    SELECT 'SCHEDULE', s.schedule_id, f.name, s.completed_at FROM schedule s JOIN family f ON f.family_id = s.family_id
                     WHERE f.is_test = FALSE AND s.status = 'COMPLETED' AND s.completed_at >= :start AND s.completed_at < :end
                     ORDER BY s.completed_at DESC
                    """;
            case ACTIVITY_COMPLETION_RATE -> """
                    SELECT 'SCHEDULE', s.schedule_id, f.name || ' · ' || s.status, CAST(s.schedule_date AS timestamp)
                      FROM schedule s JOIN family f ON f.family_id = s.family_id
                     WHERE f.is_test = FALSE AND s.status IN ('CONFIRMED', 'COMPLETED')
                       AND s.schedule_date >= CAST(:start AS date) AND s.schedule_date < CAST(:end AS date)
                     ORDER BY s.schedule_date DESC
                    """;
            case SCHEDULE_CONFIRMATION_RATE -> """
                    SELECT 'SCHEDULE', s.schedule_id, f.name || ' · ' || s.status, s.created_at
                      FROM schedule s JOIN family f ON f.family_id = s.family_id
                     WHERE f.is_test = FALSE AND s.created_at >= :start AND s.created_at < :end
                     ORDER BY s.created_at DESC
                    """;
            case WEEKLY_ACTIVE_FAMILIES -> """
                    SELECT 'FAMILY', f.family_id, f.name, f.created_at FROM family f
                     WHERE f.family_id IN (SELECT a.family_id FROM (%s) a)
                     ORDER BY f.family_id DESC
                    """.formatted(ACTIVE_FAMILIES);
            case NEW_CONNECTED_FAMILIES -> """
                    SELECT 'FAMILY', t.family_id, f.name, t.second_joined FROM (
                        SELECT m.family_id, (array_agg(m.family_joined_at ORDER BY m.family_joined_at))[2] AS second_joined
                          FROM member m WHERE m.family_joined_at IS NOT NULL GROUP BY m.family_id) t
                      JOIN family f ON f.family_id = t.family_id
                     WHERE f.is_test = FALSE AND t.second_joined >= :start AND t.second_joined < :end
                     ORDER BY t.second_joined DESC
                    """;
            case NEXT_ACTIVITY_RATE -> """
                    SELECT 'FAMILY', t.family_id, f.name, t.first_at FROM (
                        SELECT s.family_id, MIN(s.completed_at) AS first_at FROM schedule s
                         WHERE s.status = 'COMPLETED' AND s.completed_at IS NOT NULL GROUP BY s.family_id) t
                      JOIN family f ON f.family_id = t.family_id
                     WHERE f.is_test = FALSE AND t.first_at >= :start AND t.first_at < :end
                     ORDER BY t.first_at DESC
                    """;
            case RELATIONSHIP_POSITIVE_RATE -> null;
        };
        if (sql == null) {
            return List.of();
        }
        Query query = em.createNativeQuery(sql + " LIMIT 100");
        query.setParameter("start", start).setParameter("end", end);
        return query.getResultList();
    }

    // 완료 일정의 "리뷰 쓴 참여자 수 / 수락한 참여자 수" 분포. 완료 수와 실제 참여를 구분해 보여준다.
    @SuppressWarnings("unchecked")
    public List<Object[]> completionParticipation(LocalDateTime start, LocalDateTime end) {
        return em.createNativeQuery("""
                        SELECT reviewed || '/' || accepted AS label, COUNT(*) FROM (
                            SELECT s.schedule_id,
                                   (SELECT COUNT(*) FROM review r WHERE r.schedule_id = s.schedule_id) AS reviewed,
                                   (SELECT COUNT(*) FROM schedule_participant p
                                     WHERE p.schedule_id = s.schedule_id AND p.status = 'ACCEPTED') AS accepted
                              FROM schedule s JOIN family f ON f.family_id = s.family_id
                             WHERE f.is_test = FALSE AND s.status = 'COMPLETED'
                               AND s.completed_at >= :start AND s.completed_at < :end) t
                         GROUP BY reviewed, accepted ORDER BY accepted, reviewed
                        """)
                .setParameter("start", start).setParameter("end", end).getResultList();
    }

    private static final String ACTIVE_FAMILIES = """
            SELECT fq.family_id FROM member_answer a JOIN family_question fq ON fq.family_question_id = a.family_question_id
              JOIN family f ON f.family_id = fq.family_id
             WHERE f.is_test = FALSE AND a.created_at >= :start AND a.created_at < :end
            UNION
            SELECT b.family_id FROM bucket_item b JOIN family f ON f.family_id = b.family_id
             WHERE f.is_test = FALSE AND b.saved_at >= :start AND b.saved_at < :end
            UNION
            SELECT s.family_id FROM schedule s JOIN family f ON f.family_id = s.family_id
             WHERE f.is_test = FALSE AND s.created_at >= :start AND s.created_at < :end
            UNION
            SELECT s.family_id FROM schedule_participant p JOIN schedule s ON s.schedule_id = p.schedule_id
              JOIN family f ON f.family_id = s.family_id
             WHERE f.is_test = FALSE AND p.responded_at >= :start AND p.responded_at < :end
            UNION
            SELECT s.family_id FROM review r JOIN schedule s ON s.schedule_id = r.schedule_id
              JOIN family f ON f.family_id = s.family_id
             WHERE f.is_test = FALSE AND r.created_at >= :start AND r.created_at < :end
            """;

    private Measure confirmationRate(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        Object[] row = (Object[]) em.createNativeQuery("""
                        SELECT COUNT(*) FILTER (WHERE s.status IN ('CONFIRMED', 'COMPLETED')),
                               COUNT(*) FILTER (WHERE NOT (s.status = 'PENDING_RESPONSE'
                                                           AND (s.response_deadline IS NULL OR s.response_deadline >= :now)))
                          FROM schedule s JOIN family f ON f.family_id = s.family_id
                         WHERE f.is_test = FALSE AND s.created_at >= :start AND s.created_at < :end
                        """)
                .setParameter("start", start).setParameter("end", end).setParameter("now", now)
                .getSingleResult();
        return new Measure(num(row[0]), num(row[1]), true);
    }

    private Measure completionRate(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        LocalDate observableUntil = now.toLocalDate().minusDays(COMPLETION_GRACE_DAYS);
        Object[] row = (Object[]) em.createNativeQuery("""
                        SELECT COUNT(*) FILTER (WHERE s.status = 'COMPLETED'), COUNT(*)
                          FROM schedule s JOIN family f ON f.family_id = s.family_id
                         WHERE f.is_test = FALSE AND s.status IN ('CONFIRMED', 'COMPLETED')
                           AND s.schedule_date >= :startDate AND s.schedule_date < :endDate
                           AND s.schedule_date <= :observableUntil
                        """)
                .setParameter("startDate", start.toLocalDate()).setParameter("endDate", end.toLocalDate())
                .setParameter("observableUntil", observableUntil)
                .getSingleResult();
        boolean observable = !start.toLocalDate().isAfter(observableUntil);
        return new Measure(num(row[0]), num(row[1]), observable);
    }

    private Measure nextActivityRate(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        Object[] row = (Object[]) em.createNativeQuery("""
                        WITH firsts AS (
                            SELECT s.family_id, MIN(s.completed_at) AS first_at
                              FROM schedule s JOIN family f ON f.family_id = s.family_id
                             WHERE f.is_test = FALSE AND s.status = 'COMPLETED' AND s.completed_at IS NOT NULL
                             GROUP BY s.family_id)
                        SELECT COUNT(*) FILTER (WHERE EXISTS (
                                   SELECT 1 FROM schedule s2
                                    WHERE s2.family_id = firsts.family_id AND s2.status = 'COMPLETED'
                                      AND s2.completed_at > firsts.first_at
                                      AND s2.completed_at <= firsts.first_at + make_interval(days => :days))),
                               COUNT(*)
                          FROM firsts
                         WHERE first_at >= :start AND first_at < :end
                           AND first_at + make_interval(days => :days) <= :now
                        """)
                .setParameter("start", start).setParameter("end", end).setParameter("now", now)
                .setParameter("days", NEXT_ACTIVITY_DAYS)
                .getSingleResult();
        boolean observable = !end.plusDays(NEXT_ACTIVITY_DAYS).isAfter(now);
        return new Measure(num(row[0]), num(row[1]), observable);
    }

    private Measure count(String sql, LocalDateTime start, LocalDateTime end) {
        Object value = em.createNativeQuery(sql).setParameter("start", start).setParameter("end", end).getSingleResult();
        return new Measure(num(value), null, true);
    }

    private static long num(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }
}
