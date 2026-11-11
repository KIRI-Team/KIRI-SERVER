package com.kiri.server.admin.review.service;

import com.kiri.server.admin.audit.enums.AuditTargetType;
import com.kiri.server.admin.audit.service.AdminAuditQueryService;
import com.kiri.server.admin.auth.entity.Admin;
import com.kiri.server.admin.auth.repository.AdminRepository;
import com.kiri.server.admin.common.AdminErrorCode;
import com.kiri.server.admin.common.AdminException;
import com.kiri.server.admin.common.AdminPageRequest;
import com.kiri.server.admin.common.AdminPageResponse;
import com.kiri.server.admin.content.dto.MissionFormDTO;
import com.kiri.server.admin.content.service.MissionWriter;
import com.kiri.server.admin.review.dto.ReviewDTO;
import com.kiri.server.admin.review.entity.MissionReviewTask;
import com.kiri.server.admin.review.enums.ReviewTaskStatus;
import com.kiri.server.admin.review.repository.MissionReviewTaskRepository;
import com.kiri.server.domain.external.entity.ExternalRecord;
import com.kiri.server.domain.external.entity.ExternalRecordHistory;
import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.enums.ReviewReason;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.LockedField;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.mission.generator.dto.DraftResult;
import com.kiri.server.domain.mission.repository.MissionRepository;
import com.kiri.server.domain.place.entity.Place;
import com.kiri.server.domain.place.repository.PlaceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminReviewQueryService {

    private static final BigDecimal NEAR_LATITUDE_DELTA = new BigDecimal("0.0020");
    private static final BigDecimal NEAR_LONGITUDE_DELTA = new BigDecimal("0.0025");
    private static final double NEAR_METERS = 200.0;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final EntityManager em;
    private final MissionReviewTaskRepository taskRepository;
    private final AdminRepository adminRepository;
    private final MissionRepository missionRepository;
    private final PlaceRepository placeRepository;
    private final MissionWriter missionWriter;
    private final ReviewSuggestionService suggestionService;
    private final AdminAuditQueryService auditQueryService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    // ===== 탭 ① 검증 실패 =====

    @Transactional(readOnly = true)
    public AdminPageResponse<ReviewDTO.QueueItem> queue(ReviewDTO.QueueCondition c) {
        StringBuilder from = new StringBuilder("""
                 FROM external_record r
                 LEFT JOIN mission m ON m.mission_id = r.mission_id
                 LEFT JOIN place mp ON mp.place_id = m.place_id
                 LEFT JOIN place rp ON rp.place_id = r.place_id
                 LEFT JOIN mission_review_task t
                        ON t.external_record_id = r.external_record_id AND t.status IN ('OPEN', 'ON_HOLD')
                WHERE r.process_status = 'REVIEW_REQUIRED'
                """);
        Map<String, Object> params = new HashMap<>();
        if (c.reason() != null) {
            from.append(" AND r.review_reason = :reason");
            params.put("reason", c.reason().name());
        }
        if (c.source() != null) {
            from.append(" AND r.external_source = :source");
            params.put("source", c.source().name());
        }
        if (c.regionCode() != null && !c.regionCode().isBlank()) {
            from.append(" AND COALESCE(mp.region_code, rp.region_code) LIKE :region");
            params.put("region", c.regionCode().strip() + "%");
        }
        if (c.category() != null) {
            from.append(" AND m.category = :category");
            params.put("category", c.category().name());
        }
        if (c.assigneeId() != null) {
            from.append(" AND t.assignee_admin_id = :assignee");
            params.put("assignee", c.assigneeId());
        }
        if (c.taskStatus() == ReviewTaskStatus.ON_HOLD) {
            from.append(" AND t.status = 'ON_HOLD'");
        } else if (c.taskStatus() == ReviewTaskStatus.OPEN) {
            from.append(" AND (t.status IS NULL OR t.status = 'OPEN')");
        }
        if (c.minWaitingHours() != null && c.minWaitingHours() > 0) {
            from.append(" AND r.review_requested_at <= :waitBefore");
            params.put("waitBefore", LocalDateTime.now(clock).minusHours(c.minWaitingHours()));
        }
        if (c.keyword() != null && !c.keyword().isBlank()) {
            from.append(" AND (r.raw_data -> 'item' ->> 'title') ILIKE :keyword");
            params.put("keyword", "%" + c.keyword().strip() + "%");
        }

        Pageable pageable = AdminPageRequest.of(c.page(), c.size());
        Query select = em.createNativeQuery("""
                SELECT r.external_record_id, r.external_source, r.external_id, r.raw_data -> 'item' ->> 'title',
                       r.review_reason, r.review_requested_at, r.mission_id, m.title, m.status, m.category,
                       COALESCE(mp.name, rp.name), COALESCE(mp.region_code, rp.region_code),
                       t.status, t.assignee_admin_id,
                       EXISTS (SELECT 1 FROM mission_review_task a
                                WHERE a.external_record_id = r.external_record_id AND a.status = 'APPROVED')
                """ + from + " ORDER BY r.review_requested_at ASC NULLS LAST, r.external_record_id ASC"
                + " LIMIT :limit OFFSET :offset");
        Query count = em.createNativeQuery("SELECT COUNT(*)" + from);
        params.forEach((k, v) -> {
            select.setParameter(k, v);
            count.setParameter(k, v);
        });
        select.setParameter("limit", pageable.getPageSize());
        select.setParameter("offset", pageable.getOffset());

        Map<Long, String> names = adminNames();
        LocalDateTime now = LocalDateTime.now(clock);
        List<ReviewDTO.QueueItem> content = ((List<?>) select.getResultList()).stream()
                .map(row -> (Object[]) row)
                .map(r -> {
                    LocalDateTime requestedAt = toLocalDateTime(r[5]);
                    Long assignee = r[13] == null ? null : ((Number) r[13]).longValue();
                    return new ReviewDTO.QueueItem(
                            ((Number) r[0]).longValue(),
                            ExternalSource.valueOf((String) r[1]),
                            (String) r[2],
                            (String) r[3],
                            r[4] == null ? null : ReviewReason.valueOf((String) r[4]),
                            requestedAt,
                            requestedAt == null ? null : Duration.between(requestedAt, now).toHours(),
                            r[6] == null ? null : ((Number) r[6]).longValue(),
                            (String) r[7],
                            r[8] == null ? null : MissionStatus.valueOf((String) r[8]),
                            r[9] == null ? null : Category.valueOf((String) r[9]),
                            (String) r[10],
                            (String) r[11],
                            r[12] == null ? ReviewTaskStatus.OPEN : ReviewTaskStatus.valueOf((String) r[12]),
                            assignee,
                            assignee == null ? null : names.get(assignee),
                            Boolean.TRUE.equals(r[14]));
                })
                .toList();
        long total = ((Number) count.getSingleResult()).longValue();
        return AdminPageResponse.of(content, pageable.getPageNumber(), pageable.getPageSize(), total);
    }

    @Transactional(readOnly = true)
    public ReviewDTO.Detail detail(Long recordId) {
        ExternalRecord record = em.find(ExternalRecord.class, recordId);
        if (record == null) {
            throw new AdminException(AdminErrorCode.TARGET_NOT_FOUND);
        }
        JsonNode origin = suggestionService.parse(record.getRawData());
        JsonNode previous = em.createQuery(
                        "SELECT h FROM ExternalRecordHistory h WHERE h.externalRecord.id = :id ORDER BY h.id DESC",
                        ExternalRecordHistory.class)
                .setParameter("id", recordId).setMaxResults(1).getResultStream().findFirst()
                .map(h -> suggestionService.parse(h.getRawData()))
                .orElse(null);

        Optional<ReviewSuggestionService.Suggestion> suggestion = suggestionService.suggest(record);
        ReviewDTO.AutoResult autoResult = suggestion.map(s -> toAutoResult(s.autoResult())).orElse(null);

        Mission mission = record.getMission();
        ReviewDTO.CurrentMission current = mission == null ? null : new ReviewDTO.CurrentMission(
                mission.getId(), mission.getStatus(), lockedFields(mission), missionWriter.toForm(mission));

        MissionFormDTO.MissionForm basis = current != null ? current.form() : suggestion.map(ReviewSuggestionService.Suggestion::form).orElse(null);
        List<ReviewDTO.DuplicateCandidate> duplicates = basis == null ? List.of()
                : duplicateCandidates(basis, mission == null ? null : mission.getId());

        List<MissionReviewTask> tasks = taskRepository.findAllByExternalRecordIdOrderByIdDesc(recordId);
        ReviewDTO.Task openTask = tasks.stream().filter(MissionReviewTask::isOpen).findFirst().map(this::toTask).orElse(null);
        Optional<MissionReviewTask> lastApproved = tasks.stream()
                .filter(t -> t.getStatus() == ReviewTaskStatus.APPROVED).findFirst();

        return new ReviewDTO.Detail(record.getId(), record.getExternalSource(), record.getExternalId(),
                record.getSourceUrl(), record.getProcessStatus(), record.getReviewReason(),
                record.getReviewRequestedAt(), record.getSyncedAt(), origin, previous,
                changedFields(previous, origin), autoResult,
                suggestion.map(ReviewSuggestionService.Suggestion::form).orElse(null),
                current, duplicates, openTask, lastApproved.isPresent(),
                lastApproved.map(t -> readForm(t.getAppliedOverrides())).orElse(null),
                tasks.stream().map(this::toTask).toList(),
                auditQueryService.history(AuditTargetType.EXTERNAL_RECORD, recordId));
    }

    // ===== 탭 ② 공개 대기 =====

    @Transactional(readOnly = true)
    public AdminPageResponse<ReviewDTO.PendingItem> pendingPublish(ReviewDTO.PendingCondition c) {
        StringBuilder from = new StringBuilder("""
                 FROM mission m
                 JOIN place p ON p.place_id = m.place_id
                 JOIN LATERAL (SELECT r.external_record_id, r.external_source FROM external_record r
                                WHERE r.mission_id = m.mission_id AND r.process_status = 'PROCESSED'
                                ORDER BY r.external_record_id LIMIT 1) rec ON TRUE
                WHERE m.status = 'CHECK_REQUIRED'
                """);
        Map<String, Object> params = new HashMap<>();
        if (c.category() != null) {
            from.append(" AND m.category = :category");
            params.put("category", c.category().name());
        }
        if (c.regionCode() != null && !c.regionCode().isBlank()) {
            from.append(" AND p.region_code LIKE :region");
            params.put("region", c.regionCode().strip() + "%");
        }
        if (c.source() != null) {
            from.append(" AND rec.external_source = :source");
            params.put("source", c.source().name());
        }
        if (c.keyword() != null && !c.keyword().isBlank()) {
            from.append(" AND m.title ILIKE :keyword");
            params.put("keyword", "%" + c.keyword().strip() + "%");
        }
        Pageable pageable = AdminPageRequest.of(c.page(), c.size());
        Query select = em.createNativeQuery("""
                SELECT m.mission_id, m.title, m.category, m.mission_type, m.start_date, m.end_date, m.image_url,
                       p.name, p.region_code, rec.external_source, rec.external_record_id, m.created_at, m.updated_at
                """ + from + " ORDER BY m.mission_id ASC LIMIT :limit OFFSET :offset");
        Query count = em.createNativeQuery("SELECT COUNT(*)" + from);
        params.forEach((k, v) -> {
            select.setParameter(k, v);
            count.setParameter(k, v);
        });
        select.setParameter("limit", pageable.getPageSize());
        select.setParameter("offset", pageable.getOffset());

        List<ReviewDTO.PendingItem> content = ((List<?>) select.getResultList()).stream()
                .map(row -> (Object[]) row)
                .map(r -> new ReviewDTO.PendingItem(
                        ((Number) r[0]).longValue(), (String) r[1],
                        Category.valueOf((String) r[2]), MissionType.valueOf((String) r[3]),
                        toLocalDate(r[4]), toLocalDate(r[5]), (String) r[6], (String) r[7], (String) r[8],
                        ExternalSource.valueOf((String) r[9]), ((Number) r[10]).longValue(),
                        toLocalDateTime(r[11]), toLocalDateTime(r[12])))
                .toList();
        long total = ((Number) count.getSingleResult()).longValue();
        return AdminPageResponse.of(content, pageable.getPageNumber(), pageable.getPageSize(), total);
    }

    // ===== 공통 =====

    List<ReviewDTO.DuplicateCandidate> duplicateCandidates(MissionFormDTO.MissionForm form, Long selfMissionId) {
        Map<Long, Mission> candidates = new LinkedHashMap<>();
        if (form.title() != null) {
            missionRepository.findAllWithPlaceByTitle(form.title().strip()).forEach(m -> candidates.put(m.getId(), m));
        }
        MissionFormDTO.PlaceForm place = form.place();
        if (place != null && place.latitude() != null && place.longitude() != null) {
            List<Place> nearby = placeRepository.findAllWithin(
                    place.latitude().subtract(NEAR_LATITUDE_DELTA), place.latitude().add(NEAR_LATITUDE_DELTA),
                    place.longitude().subtract(NEAR_LONGITUDE_DELTA), place.longitude().add(NEAR_LONGITUDE_DELTA));
            if (!nearby.isEmpty()) {
                em.createQuery("SELECT m FROM Mission m JOIN FETCH m.place WHERE m.place IN :places", Mission.class)
                        .setParameter("places", nearby).getResultList()
                        .forEach(m -> candidates.putIfAbsent(m.getId(), m));
            }
        }
        candidates.remove(selfMissionId);
        return candidates.values().stream()
                .map(m -> {
                    Double distance = place == null || place.latitude() == null || place.longitude() == null ? null
                            : distanceMeters(place.latitude(), place.longitude(), m.getPlace().getLatitude(), m.getPlace().getLongitude());
                    return new ReviewDTO.DuplicateCandidate(m.getId(), m.getTitle(), m.getStatus(), m.getMissionType(),
                            m.getStartDate(), m.getEndDate(), m.getPlace().getName(), distance,
                            form.title() != null && form.title().strip().equals(m.getTitle()));
                })
                .filter(c -> c.sameTitle() || (c.distanceMeters() != null && c.distanceMeters() <= NEAR_METERS))
                .limit(20)
                .toList();
    }

    // 직전 원본과 비교해 바뀐 필드 경로(item.title, common.overview 등)를 돌려준다.
    static List<String> changedFields(JsonNode previous, JsonNode current) {
        if (previous == null || current == null) {
            return List.of();
        }
        List<String> changed = new ArrayList<>();
        for (String section : List.of("item", "common", "intro")) {
            JsonNode a = previous.path(section);
            JsonNode b = current.path(section);
            List<String> keys = new ArrayList<>();
            Iterator<String> ai = a.propertyNames().iterator();
            ai.forEachRemaining(keys::add);
            b.propertyNames().forEach(k -> {
                if (!keys.contains(k)) {
                    keys.add(k);
                }
            });
            for (String key : keys) {
                if (!Objects.equals(a.get(key), b.get(key))) {
                    changed.add(section + "." + key);
                }
            }
        }
        if (!Objects.equals(previous.get("info"), current.get("info"))) {
            changed.add("info");
        }
        return changed;
    }

    private ReviewDTO.AutoResult toAutoResult(DraftResult result) {
        return switch (result) {
            case DraftResult.Ready ignored -> new ReviewDTO.AutoResult("READY", null);
            case DraftResult.ReviewRequired r -> new ReviewDTO.AutoResult("REVIEW_REQUIRED", r.reason());
            case DraftResult.Ignored i -> new ReviewDTO.AutoResult("IGNORED", i.reason());
        };
    }

    ReviewDTO.Task toTask(MissionReviewTask t) {
        return new ReviewDTO.Task(t.getId(), t.getStatus(), t.getReviewReason(), t.getAssigneeAdminId(), t.getNote(),
                t.getResolvedBy(), t.getResolvedAt(), t.getCreatedAt());
    }

    static List<LockedField> lockedFields(Mission mission) {
        return java.util.Arrays.stream(LockedField.values()).filter(mission::isLocked).toList();
    }

    private MissionFormDTO.MissionForm readForm(String json) {
        return json == null ? null : objectMapper.readValue(json, MissionFormDTO.MissionForm.class);
    }

    private Map<Long, String> adminNames() {
        return adminRepository.findAll().stream().collect(Collectors.toMap(Admin::getId, Admin::getName, (a, b) -> a));
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        return switch (value) {
            case null -> null;
            case LocalDateTime v -> v;
            case Timestamp v -> v.toLocalDateTime();
            case OffsetDateTime v -> v.atZoneSameInstant(SEOUL).toLocalDateTime();
            case java.time.Instant v -> LocalDateTime.ofInstant(v, SEOUL);
            default -> throw new IllegalStateException("지원하지 않는 시각 타입: " + value.getClass());
        };
    }

    private static LocalDate toLocalDate(Object value) {
        return switch (value) {
            case null -> null;
            case LocalDate v -> v;
            case java.sql.Date v -> v.toLocalDate();
            default -> throw new IllegalStateException("지원하지 않는 날짜 타입: " + value.getClass());
        };
    }

    private static double distanceMeters(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double dPhi = phi2 - phi1;
        double dLambda = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
        double a = Math.pow(Math.sin(dPhi / 2), 2) + Math.cos(phi1) * Math.cos(phi2) * Math.pow(Math.sin(dLambda / 2), 2);
        return 2 * 6_371_000 * Math.asin(Math.sqrt(a));
    }
}
