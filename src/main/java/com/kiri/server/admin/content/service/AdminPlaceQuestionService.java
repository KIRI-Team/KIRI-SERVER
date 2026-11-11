package com.kiri.server.admin.content.service;

import com.kiri.server.admin.audit.enums.AuditAction;
import com.kiri.server.admin.audit.enums.AuditTargetType;
import com.kiri.server.admin.audit.service.AdminAuditLogger;
import com.kiri.server.admin.common.AdminErrorCode;
import com.kiri.server.admin.common.AdminException;
import com.kiri.server.admin.common.AdminPageRequest;
import com.kiri.server.admin.common.AdminPageResponse;
import com.kiri.server.admin.common.AdminValidationException;
import com.kiri.server.admin.content.dto.ContentDTO;
import com.kiri.server.admin.content.dto.MissionFormDTO;
import com.kiri.server.domain.mission.enums.LockedField;
import com.kiri.server.domain.place.entity.Place;
import com.kiri.server.domain.place.entity.PlaceContent;
import com.kiri.server.domain.question.entity.Question;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AdminPlaceQuestionService {

    private static final BigDecimal MIN_LATITUDE = new BigDecimal("33");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("39");
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("124");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("132");

    private final EntityManager em;
    private final AdminAuditLogger auditLogger;

    // ===== 장소 =====

    @Transactional(readOnly = true)
    public AdminPageResponse<ContentDTO.PlaceItem> searchPlaces(ContentDTO.PlaceCondition c) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> params = new HashMap<>();
        if (c.keyword() != null && !c.keyword().isBlank()) {
            where.append(" AND LOWER(p.name) LIKE :kw");
            params.put("kw", "%" + c.keyword().strip().toLowerCase() + "%");
        }
        if (c.regionCode() != null && !c.regionCode().isBlank()) {
            where.append(" AND p.regionCode LIKE :region");
            params.put("region", c.regionCode().strip() + "%");
        }
        Pageable pageable = AdminPageRequest.of(c.page(), c.size());
        TypedQuery<Place> query = em.createQuery("SELECT p FROM Place p" + where + " ORDER BY p.id DESC", Place.class);
        TypedQuery<Long> count = em.createQuery("SELECT COUNT(p) FROM Place p" + where, Long.class);
        params.forEach((k, v) -> {
            query.setParameter(k, v);
            count.setParameter(k, v);
        });
        List<Place> places = query.setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize()).getResultList();
        Map<Long, Long> missionCounts = new HashMap<>();
        if (!places.isEmpty()) {
            em.createQuery("SELECT m.place.id, COUNT(m) FROM Mission m WHERE m.place IN :places GROUP BY m.place.id", Object[].class)
                    .setParameter("places", places).getResultList()
                    .forEach(row -> missionCounts.put((Long) row[0], (Long) row[1]));
        }
        List<ContentDTO.PlaceItem> content = places.stream()
                .map(p -> toPlaceItem(p, missionCounts.getOrDefault(p.getId(), 0L))).toList();
        return AdminPageResponse.of(content, pageable.getPageNumber(), pageable.getPageSize(), count.getSingleResult());
    }

    @Transactional(readOnly = true)
    public ContentDTO.PlaceItem getPlace(Long placeId) {
        Place place = findPlace(placeId);
        return toPlaceItem(place, missionCount(place));
    }

    @Transactional
    public ContentDTO.PlaceItem createPlace(Long adminId, MissionFormDTO.PlaceForm form) {
        validatePlace(form);
        Place place = Place.create(toContent(form));
        em.persist(place);
        auditLogger.log(adminId, AuditAction.CREATE, AuditTargetType.PLACE, place.getId(), null, null, form);
        return toPlaceItem(place, 0);
    }

    // 장소 공통 정보를 고친다. 바뀐 필드는 잠겨서 외부 재수집이 덮어쓰지 않는다.
    @Transactional
    public ContentDTO.PlaceItem updatePlace(Long adminId, Long placeId, MissionFormDTO.PlaceForm form) {
        validatePlace(form);
        Place place = findPlace(placeId);
        Map<String, Object> before = placeSnapshot(place);
        var changed = place.applyAdminContent(toContent(form));
        Map<String, Object> after = placeSnapshot(place);
        after.put("changedFields", changed);
        auditLogger.log(adminId, AuditAction.UPDATE, AuditTargetType.PLACE, placeId, null, before, after);
        return toPlaceItem(place, missionCount(place));
    }

    // ===== 질문 =====

    @Transactional(readOnly = true)
    public AdminPageResponse<ContentDTO.QuestionItem> searchQuestions(ContentDTO.QuestionCondition c) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> params = new HashMap<>();
        if (c.active() != null) {
            where.append(" AND q.isActive = :active");
            params.put("active", c.active());
        }
        if (c.keyword() != null && !c.keyword().isBlank()) {
            where.append(" AND q.content LIKE :kw");
            params.put("kw", "%" + c.keyword().strip() + "%");
        }
        Pageable pageable = AdminPageRequest.of(c.page(), c.size());
        TypedQuery<Question> query = em.createQuery("SELECT q FROM Question q" + where + " ORDER BY q.id DESC", Question.class);
        TypedQuery<Long> count = em.createQuery("SELECT COUNT(q) FROM Question q" + where, Long.class);
        params.forEach((k, v) -> {
            query.setParameter(k, v);
            count.setParameter(k, v);
        });
        List<Question> questions = query.setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize()).getResultList();
        Map<Long, Long> assigned = new HashMap<>();
        if (!questions.isEmpty()) {
            em.createQuery("SELECT fq.question.id, COUNT(fq) FROM FamilyQuestion fq WHERE fq.question IN :qs GROUP BY fq.question.id", Object[].class)
                    .setParameter("qs", questions).getResultList()
                    .forEach(row -> assigned.put((Long) row[0], (Long) row[1]));
        }
        List<ContentDTO.QuestionItem> content = questions.stream()
                .map(q -> toQuestionItem(q, assigned.getOrDefault(q.getId(), 0L))).toList();
        return AdminPageResponse.of(content, pageable.getPageNumber(), pageable.getPageSize(), count.getSingleResult());
    }

    @Transactional
    public ContentDTO.QuestionItem createQuestion(Long adminId, ContentDTO.QuestionRequest request) {
        Question question = Question.builder().content(request.content().strip())
                .fixedDay(request.fixedDay()).tasteType(request.tasteType()).build();
        em.persist(question);
        auditLogger.log(adminId, AuditAction.CREATE, AuditTargetType.QUESTION, question.getId(), null, null, request);
        return toQuestionItem(question, 0);
    }

    // 이미 가족에게 배정된 질문은 문구를 바꾸면 과거 기록이 달라지므로 막는다.
    @Transactional
    public ContentDTO.QuestionItem updateQuestion(Long adminId, Long questionId, ContentDTO.QuestionRequest request) {
        Question question = findQuestion(questionId);
        long assigned = assignedCount(question);
        String content = request.content().strip();
        if (assigned > 0 && !content.equals(question.getContent())) {
            throw new AdminException(AdminErrorCode.QUESTION_ALREADY_ASSIGNED);
        }
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("content", question.getContent());
        before.put("fixedDay", question.getFixedDay());
        before.put("tasteType", question.getTasteType());
        question.update(content, request.fixedDay(), request.tasteType());
        auditLogger.log(adminId, AuditAction.UPDATE, AuditTargetType.QUESTION, questionId, null, before, request);
        return toQuestionItem(question, assigned);
    }

    @Transactional
    public ContentDTO.QuestionItem setQuestionActive(Long adminId, Long questionId, boolean active) {
        Question question = findQuestion(questionId);
        boolean before = Boolean.TRUE.equals(question.getIsActive());
        if (active) {
            question.activate();
        } else {
            question.deactivate();
        }
        auditLogger.log(adminId, active ? AuditAction.RESTORE : AuditAction.HIDE, AuditTargetType.QUESTION, questionId,
                null, Map.of("active", before), Map.of("active", active));
        return toQuestionItem(question, assignedCount(question));
    }

    // ===== 공통 =====

    private static void validatePlace(MissionFormDTO.PlaceForm form) {
        List<MissionFormDTO.FieldError> errors = new ArrayList<>();
        if (form == null) {
            throw new AdminValidationException(List.of(new MissionFormDTO.FieldError("place", "장소 입력값이 없습니다.")));
        }
        if (form.name() == null || form.name().isBlank()) {
            errors.add(new MissionFormDTO.FieldError("name", "장소명은 필수입니다."));
        } else if (form.name().strip().length() > 100) {
            errors.add(new MissionFormDTO.FieldError("name", "장소명은 100자 이하여야 합니다."));
        }
        if (form.placeType() == null) {
            errors.add(new MissionFormDTO.FieldError("placeType", "장소 유형은 필수입니다."));
        }
        if (form.latitude() == null || form.longitude() == null) {
            errors.add(new MissionFormDTO.FieldError("coordinate", "좌표는 필수입니다."));
        } else if (form.latitude().compareTo(MIN_LATITUDE) < 0 || form.latitude().compareTo(MAX_LATITUDE) > 0
                || form.longitude().compareTo(MIN_LONGITUDE) < 0 || form.longitude().compareTo(MAX_LONGITUDE) > 0) {
            errors.add(new MissionFormDTO.FieldError("coordinate", "좌표가 국내 범위를 벗어났습니다."));
        }
        if (!errors.isEmpty()) {
            throw new AdminValidationException(errors);
        }
    }

    private static PlaceContent toContent(MissionFormDTO.PlaceForm form) {
        return PlaceContent.builder()
                .name(form.name().strip()).placeType(form.placeType()).regionCode(form.regionCode())
                .latitude(form.latitude()).longitude(form.longitude()).address(form.address())
                .openingTime(form.openingTime()).closingTime(form.closingTime())
                .imageUrl(form.imageUrl()).imageSource(form.imageSource())
                .build();
    }

    private ContentDTO.PlaceItem toPlaceItem(Place p, long missionCount) {
        return new ContentDTO.PlaceItem(p.getId(), p.getName(), p.getPlaceType(), p.getRegionCode(), p.getLatitude(),
                p.getLongitude(), p.getAddress(), p.getImageUrl(), missionCount,
                Arrays.stream(LockedField.values()).filter(p::isLocked).toList());
    }

    private static Map<String, Object> placeSnapshot(Place p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", p.getName());
        map.put("placeType", p.getPlaceType());
        map.put("regionCode", p.getRegionCode());
        map.put("latitude", p.getLatitude());
        map.put("longitude", p.getLongitude());
        map.put("address", p.getAddress());
        map.put("imageUrl", p.getImageUrl());
        return map;
    }

    private ContentDTO.QuestionItem toQuestionItem(Question q, long assigned) {
        return new ContentDTO.QuestionItem(q.getId(), q.getContent(), Boolean.TRUE.equals(q.getIsActive()),
                q.getFixedDay(), q.getTasteType(), assigned, q.getCreatedAt());
    }

    private long missionCount(Place place) {
        return em.createQuery("SELECT COUNT(m) FROM Mission m WHERE m.place = :place", Long.class)
                .setParameter("place", place).getSingleResult();
    }

    private long assignedCount(Question question) {
        return em.createQuery("SELECT COUNT(fq) FROM FamilyQuestion fq WHERE fq.question = :q", Long.class)
                .setParameter("q", question).getSingleResult();
    }

    private Place findPlace(Long placeId) {
        Place place = em.find(Place.class, placeId);
        if (place == null) {
            throw new AdminException(AdminErrorCode.TARGET_NOT_FOUND);
        }
        return place;
    }

    private Question findQuestion(Long questionId) {
        Question question = em.find(Question.class, questionId);
        if (question == null || Objects.isNull(question.getId())) {
            throw new AdminException(AdminErrorCode.TARGET_NOT_FOUND);
        }
        return question;
    }
}
