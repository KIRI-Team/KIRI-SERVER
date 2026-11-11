package com.kiri.server.admin.dashboard.service;

import com.kiri.server.admin.dashboard.dto.DashboardDTO;
import com.kiri.server.admin.dashboard.dto.DashboardDTO.DataStatus;
import com.kiri.server.admin.dashboard.dto.DashboardDTO.KpiKey;
import com.kiri.server.admin.dashboard.dto.DashboardDTO.KpiUnit;
import com.kiri.server.domain.external.entity.ExternalSyncState;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final int TREND_WEEKS = 8;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Map<KpiKey, String> NAMES = new EnumMap<>(Map.of(
            KpiKey.WEEKLY_COMPLETED_FAMILIES, "주간 활동 완료 가족 수",
            KpiKey.WEEKLY_COMPLETED_SCHEDULES, "주간 공동 활동 완료 일정 수",
            KpiKey.NEW_CONNECTED_FAMILIES, "신규 연결 가족 수",
            KpiKey.WEEKLY_ACTIVE_FAMILIES, "주간 활성 가족 수",
            KpiKey.SCHEDULE_CONFIRMATION_RATE, "일정 확정률",
            KpiKey.ACTIVITY_COMPLETION_RATE, "활동 완료율",
            KpiKey.NEXT_ACTIVITY_RATE, "다음 활동률",
            KpiKey.RELATIONSHIP_POSITIVE_RATE, "관계 경험 긍정 응답률"));

    private final KpiCalculator calculator;
    private final EntityManager em;
    private final Clock clock;

    private final Map<LocalDate, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(List<DashboardDTO.Kpi> kpis, Instant at) {
    }

    // weekOf가 없으면 직전에 끝난 주(KST 월~일). fresh=true면 캐시를 쓰지 않는다.
    @Transactional(readOnly = true)
    public List<DashboardDTO.Kpi> kpis(LocalDate weekOf, boolean fresh) {
        LocalDate weekStart = weekStart(weekOf);
        Cached cached = cache.get(weekStart);
        if (!fresh && cached != null && cached.at().plus(CACHE_TTL).isAfter(clock.instant())) {
            return cached.kpis();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<DashboardDTO.Kpi> kpis = new ArrayList<>();
        for (KpiKey key : KpiKey.values()) {
            kpis.add(kpi(key, weekStart, now));
        }
        cache.put(weekStart, new Cached(kpis, clock.instant()));
        return kpis;
    }

    @Transactional(readOnly = true)
    public DashboardDTO.KpiDetail detail(KpiKey key, LocalDate weekOf) {
        LocalDate weekStart = weekStart(weekOf);
        LocalDateTime now = LocalDateTime.now(clock);
        DashboardDTO.Kpi kpi = kpi(key, weekStart, now);
        LocalDateTime start = weekStart.atStartOfDay();
        LocalDateTime end = start.plusDays(7);
        List<DashboardDTO.DetailRow> rows = calculator.numeratorRows(key, start, end, now).stream()
                .map(r -> new DashboardDTO.DetailRow((String) r[0], ((Number) r[1]).longValue(),
                        r[2] == null ? null : r[2].toString(), toLocalDateTime(r[3])))
                .toList();
        List<DashboardDTO.Breakdown> breakdown = key == KpiKey.WEEKLY_COMPLETED_SCHEDULES
                ? calculator.completionParticipation(start, end).stream()
                .map(r -> new DashboardDTO.Breakdown("리뷰 " + r[0].toString().replace("/", "명 / 수락 ") + "명",
                        ((Number) r[1]).longValue()))
                .toList()
                : List.of();
        return new DashboardDTO.KpiDetail(kpi, rows, breakdown);
    }

    @Transactional(readOnly = true)
    public DashboardDTO.Ops ops() {
        LocalDateTime now = LocalDateTime.now(clock);
        Object[] review = (Object[]) em.createNativeQuery("""
                        SELECT COUNT(*), MIN(r.review_requested_at),
                               COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM mission_review_task t
                                   WHERE t.external_record_id = r.external_record_id AND t.status = 'ON_HOLD'))
                          FROM external_record r WHERE r.process_status = 'REVIEW_REQUIRED'
                        """).getSingleResult();
        LocalDateTime oldest = toLocalDateTime(review[1]);
        long pendingPublish = count("""
                SELECT COUNT(*) FROM mission m WHERE m.status = 'CHECK_REQUIRED'
                   AND EXISTS (SELECT 1 FROM external_record r WHERE r.mission_id = m.mission_id AND r.process_status = 'PROCESSED')
                """);
        List<DashboardDTO.SyncSource> sources = em.createQuery("SELECT s FROM ExternalSyncState s ORDER BY s.externalSource", ExternalSyncState.class)
                .getResultList().stream()
                .map(s -> new DashboardDTO.SyncSource(s.getExternalSource(), s.getLastStatus(), s.getLastErrorCode(),
                        s.getLastSuccessAt(), s.isAdminPaused()))
                .toList();
        long picksHidden = ((Number) em.createNativeQuery("""
                        SELECT COUNT(*) FROM kiri_pick k JOIN mission m ON m.mission_id = k.mission_id
                         WHERE k.active = TRUE AND k.start_at <= :now AND k.end_at > :now AND m.status <> 'ACTIVE'
                        """).setParameter("now", now).getSingleResult()).longValue();
        long mismatch = count("""
                SELECT COUNT(DISTINCT s.schedule_id) FROM schedule s JOIN review r ON r.schedule_id = s.schedule_id
                 WHERE s.status <> 'COMPLETED'
                """);
        return new DashboardDTO.Ops(((Number) review[0]).longValue(),
                oldest == null ? null : Duration.between(oldest, now).toHours(),
                ((Number) review[2]).longValue(), pendingPublish, sources,
                count("SELECT COUNT(*) FROM external_sync_retry"), picksHidden, mismatch, now);
    }

    private DashboardDTO.Kpi kpi(KpiKey key, LocalDate weekStart, LocalDateTime now) {
        KpiUnit unit = key.name().endsWith("_RATE") ? KpiUnit.RATE : KpiUnit.COUNT;
        String formula = KpiCalculator.FORMULAS.get(key);
        try {
            List<DashboardDTO.TrendPoint> trend = new ArrayList<>();
            Point current = null;
            Point previous = null;
            for (int i = TREND_WEEKS - 1; i >= 0; i--) {
                LocalDate start = weekStart.minusWeeks(i);
                Point point = point(key, start, now);
                trend.add(new DashboardDTO.TrendPoint(start, point.value(), point.status()));
                if (i == 1) {
                    previous = point;
                }
                if (i == 0) {
                    current = point;
                }
            }
            KpiCalculator.Measure m = current.measure();
            return new DashboardDTO.Kpi(key, NAMES.get(key), unit, weekStart, weekStart.plusDays(6),
                    current.value(), previous.value(),
                    m == null ? null : m.numerator(), m == null ? null : m.denominator(),
                    m == null ? null : m.denominator() != null ? m.denominator() : m.numerator(),
                    trend, formula, current.status(), now);
        } catch (RuntimeException e) {
            log.error("[Dashboard] KPI 계산 실패: key={}", key, e);
            return new DashboardDTO.Kpi(key, NAMES.get(key), unit, weekStart, weekStart.plusDays(6),
                    null, null, null, null, null, List.of(), formula, DataStatus.ERROR, now);
        }
    }

    private record Point(KpiCalculator.Measure measure, Double value, DataStatus status) {
    }

    private Point point(KpiKey key, LocalDate weekStart, LocalDateTime now) {
        LocalDateTime start = weekStart.atStartOfDay();
        KpiCalculator.Measure m = calculator.measure(key, start, start.plusDays(7), now);
        if (m == null) {
            return new Point(null, null, DataStatus.NOT_COLLECTED);
        }
        if (!m.observable()) {
            return new Point(m, null, DataStatus.INSUFFICIENT_PERIOD);
        }
        if (m.denominator() == null) {
            return new Point(m, (double) m.numerator(), DataStatus.OK);
        }
        // 분모가 0이면 정상 0건이지만 비율은 정의되지 않으므로 값은 비운다.
        Double rate = m.denominator() == 0 ? null : Math.round(m.numerator() * 10000.0 / m.denominator()) / 10000.0;
        return new Point(m, rate, DataStatus.OK);
    }

    LocalDate weekStart(LocalDate weekOf) {
        LocalDate base = weekOf != null ? weekOf : LocalDate.now(clock).minusWeeks(1);
        return base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private long count(String sql) {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        return switch (value) {
            case null -> null;
            case LocalDateTime v -> v;
            case Timestamp v -> v.toLocalDateTime();
            case OffsetDateTime v -> v.atZoneSameInstant(SEOUL).toLocalDateTime();
            case Instant v -> LocalDateTime.ofInstant(v, SEOUL);
            case LocalDate v -> v.atStartOfDay();
            default -> null;
        };
    }
}
