package com.kiri.server.admin.content.service;

import com.kiri.server.admin.audit.enums.AuditAction;
import com.kiri.server.admin.audit.enums.AuditTargetType;
import com.kiri.server.admin.audit.service.AdminAuditLogger;
import com.kiri.server.admin.common.AdminErrorCode;
import com.kiri.server.admin.common.AdminException;
import com.kiri.server.admin.common.AdminPageRequest;
import com.kiri.server.admin.common.AdminPageResponse;
import com.kiri.server.admin.common.AdminValidationException;
import com.kiri.server.admin.common.Reasons;
import com.kiri.server.admin.content.dto.ContentDTO;
import com.kiri.server.admin.content.dto.MissionFormDTO;
import com.kiri.server.domain.bucket.enums.BucketSaveState;
import com.kiri.server.domain.external.entity.ExternalRecord;
import com.kiri.server.domain.mission.converter.MissionConverter;
import com.kiri.server.domain.mission.dto.MissionResDTO;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.LockedField;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.repository.MissionChecklistItemRepository;
import com.kiri.server.domain.mission.repository.MissionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminMissionService {

    private final EntityManager em;
    private final MissionRepository missionRepository;
    private final MissionChecklistItemRepository checklistRepository;
    private final MissionWriter missionWriter;
    private final AdminAuditLogger auditLogger;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminPageResponse<ContentDTO.MissionItem> search(ContentDTO.MissionCondition c) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> params = new HashMap<>();
        if (c.status() != null) {
            where.append(" AND m.status = :status");
            params.put("status", c.status());
        }
        if (c.category() != null) {
            where.append(" AND m.category = :category");
            params.put("category", c.category());
        }
        if (c.origin() != null) {
            where.append(" AND m.origin = :origin");
            params.put("origin", c.origin());
        }
        if (c.missionType() != null) {
            where.append(" AND m.missionType = :missionType");
            params.put("missionType", c.missionType());
        }
        if (c.regionCode() != null && !c.regionCode().isBlank()) {
            where.append(" AND p.regionCode LIKE :region");
            params.put("region", c.regionCode().strip() + "%");
        }
        if (c.keyword() != null && !c.keyword().isBlank()) {
            where.append(" AND (LOWER(m.title) LIKE :kw OR LOWER(p.name) LIKE :kw)");
            params.put("kw", "%" + c.keyword().strip().toLowerCase() + "%");
        }
        Pageable pageable = AdminPageRequest.of(c.page(), c.size());
        TypedQuery<Mission> query = em.createQuery(
                "SELECT m FROM Mission m JOIN FETCH m.place p" + where + " ORDER BY m.id DESC", Mission.class);
        TypedQuery<Long> count = em.createQuery("SELECT COUNT(m) FROM Mission m JOIN m.place p" + where, Long.class);
        params.forEach((k, v) -> {
            query.setParameter(k, v);
            count.setParameter(k, v);
        });
        List<ContentDTO.MissionItem> content = query.setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize()).getResultList().stream()
                .map(m -> new ContentDTO.MissionItem(m.getId(), m.getTitle(), m.getStatus(), m.getOrigin(),
                        m.getCategory(), m.getMissionType(), m.getStartDate(), m.getEndDate(),
                        m.getPlace().getId(), m.getPlace().getName(), m.getPlace().getRegionCode(), m.getImageUrl(),
                        lockedFields(m).size(), m.getCreatedAt(), m.getUpdatedAt()))
                .toList();
        return AdminPageResponse.of(content, pageable.getPageNumber(), pageable.getPageSize(), count.getSingleResult());
    }

    @Transactional(readOnly = true)
    public ContentDTO.MissionDetail detail(Long missionId) {
        return toDetail(find(missionId));
    }

    // 수동 등록은 초안(DRAFT)으로 저장한다. 공개는 publish로 따로 한다.
    @Transactional
    public ContentDTO.MissionDetail create(Long adminId, MissionFormDTO.MissionForm form) {
        requireValid(form == null ? null : MissionFormValidator.validateForDraft(form));
        Mission mission = missionWriter.createManual(form).mission();
        auditLogger.log(adminId, AuditAction.CREATE, AuditTargetType.MISSION, mission.getId(), null, null, form);
        return toDetail(mission);
    }

    // 공개 중인 미션을 고치면 사유가 필요하다. 바뀐 필드는 잠긴다.
    @Transactional
    public ContentDTO.MissionDetail update(Long adminId, Long missionId, ContentDTO.UpdateMissionRequest request) {
        Mission mission = lock(missionId);
        MissionFormDTO.MissionForm form = request == null ? null : request.form();
        String reason = mission.getStatus() == MissionStatus.ACTIVE
                ? Reasons.require(request == null ? null : request.reason())
                : request == null ? null : request.reason();
        requireValid(form == null ? null : mission.getStatus() == MissionStatus.DRAFT
                ? MissionFormValidator.validateForDraft(form)
                : MissionFormValidator.validateForPublish(form));
        MissionFormDTO.MissionForm before = missionWriter.toForm(mission);
        MissionWriter.WriteResult written = missionWriter.update(mission, form);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("form", form);
        after.put("changedFields", written.changedFields());
        auditLogger.log(adminId, AuditAction.UPDATE, AuditTargetType.MISSION, missionId, reason, before, after);
        return toDetail(mission);
    }

    @Transactional
    public ContentDTO.MissionDetail publish(Long adminId, Long missionId) {
        Mission mission = lock(missionId);
        if (mission.getStatus() != MissionStatus.DRAFT) {
            throw new AdminException(AdminErrorCode.INVALID_STATUS);
        }
        MissionFormDTO.ValidationResult result = MissionFormValidator.validateForPublish(missionWriter.toForm(mission));
        if (!result.valid()) {
            throw new AdminValidationException(result.errors());
        }
        mission.publishDraft(LocalDate.now(clock));
        auditLogger.log(adminId, AuditAction.PUBLISH, AuditTargetType.MISSION, missionId, null,
                Map.of("status", MissionStatus.DRAFT), Map.of("status", mission.getStatus()));
        return toDetail(mission);
    }

    @Transactional
    public ContentDTO.MissionDetail hide(Long adminId, Long missionId, String reason) {
        String validReason = Reasons.require(reason);
        Mission mission = lock(missionId);
        MissionStatus before = mission.getStatus();
        if (before == MissionStatus.HIDDEN || before == MissionStatus.CANCELLED) {
            throw new AdminException(AdminErrorCode.INVALID_STATUS);
        }
        mission.hide();
        auditLogger.log(adminId, AuditAction.HIDE, AuditTargetType.MISSION, missionId, validReason,
                Map.of("status", before), Map.of("status", mission.getStatus()));
        return toDetail(mission);
    }

    // 숨긴 미션을 다시 공개한다. 기간이 지났으면 종료 상태가 된다.
    @Transactional
    public ContentDTO.MissionDetail restore(Long adminId, Long missionId, String reason) {
        String validReason = Reasons.require(reason);
        Mission mission = lock(missionId);
        if (mission.getStatus() != MissionStatus.HIDDEN) {
            throw new AdminException(AdminErrorCode.INVALID_STATUS);
        }
        mission.restore(LocalDate.now(clock));
        auditLogger.log(adminId, AuditAction.RESTORE, AuditTargetType.MISSION, missionId, validReason,
                Map.of("status", MissionStatus.HIDDEN), Map.of("status", mission.getStatus()));
        return toDetail(mission);
    }

    // 잠금을 풀면 다음 외부 재수집 때 원본 값으로 돌아간다.
    @Transactional
    public ContentDTO.MissionDetail unlock(Long adminId, Long missionId, LockedField field) {
        Mission mission = lock(missionId);
        mission.unlock(field);
        auditLogger.log(adminId, AuditAction.UNLOCK_FIELD, AuditTargetType.MISSION, missionId, null,
                Map.of("lockedField", field), null);
        return toDetail(mission);
    }

    // 앱 상세 화면과 같은 응답 형태. 초안도 볼 수 있도록 상태 검사를 하지 않는다.
    @Transactional(readOnly = true)
    public MissionResDTO.Detail preview(Long missionId) {
        Mission mission = find(missionId);
        return MissionConverter.toDetail(mission,
                checklistRepository.findAllByMissionOrderByItemTypeAscDisplayOrderAsc(mission),
                sourceUrl(mission), BucketSaveState.NONE);
    }

    private ContentDTO.MissionDetail toDetail(Mission mission) {
        ExternalRecord record = em.createQuery(
                        "SELECT r FROM ExternalRecord r WHERE r.mission = :mission ORDER BY r.id", ExternalRecord.class)
                .setParameter("mission", mission).setMaxResults(1).getResultStream().findFirst().orElse(null);
        return new ContentDTO.MissionDetail(mission.getId(), mission.getStatus(), mission.getOrigin(),
                lockedFields(mission), record == null ? null : record.getId(),
                record == null ? null : record.getSourceUrl(), mission.getCancelReason(),
                missionWriter.toForm(mission), mission.getCreatedAt(), mission.getUpdatedAt());
    }

    private String sourceUrl(Mission mission) {
        return em.createQuery("""
                        SELECT r.sourceUrl FROM ExternalRecord r
                         WHERE r.mission = :mission AND r.sourceUrl IS NOT NULL ORDER BY r.id
                        """, String.class)
                .setParameter("mission", mission).setMaxResults(1).getResultStream().findFirst().orElse(null);
    }

    private Mission find(Long missionId) {
        return missionRepository.findWithPlaceById(missionId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.TARGET_NOT_FOUND));
    }

    // 외부 동기화와 동시에 고치지 않도록 미션 행을 잠근다.
    private Mission lock(Long missionId) {
        Mission mission = em.find(Mission.class, missionId, LockModeType.PESSIMISTIC_WRITE);
        if (mission == null) {
            throw new AdminException(AdminErrorCode.TARGET_NOT_FOUND);
        }
        return mission;
    }

    private static void requireValid(MissionFormDTO.ValidationResult result) {
        if (result == null) {
            throw new AdminValidationException(List.of(new MissionFormDTO.FieldError("form", "입력값이 없습니다.")));
        }
        if (!result.valid()) {
            throw new AdminValidationException(result.errors());
        }
    }

    static List<LockedField> lockedFields(Mission mission) {
        return Arrays.stream(LockedField.values()).filter(mission::isLocked).toList();
    }
}
