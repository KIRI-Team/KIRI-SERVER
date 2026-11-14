package com.kiri.server.admin.pick.service;

import com.kiri.server.admin.audit.enums.AuditAction;
import com.kiri.server.admin.audit.enums.AuditTargetType;
import com.kiri.server.admin.audit.service.AdminAuditLogger;
import com.kiri.server.admin.common.AdminErrorCode;
import com.kiri.server.admin.common.AdminException;
import com.kiri.server.admin.pick.dto.PickDTO;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.repository.MissionRepository;
import com.kiri.server.domain.pick.entity.KiriPick;
import com.kiri.server.domain.pick.enums.PickType;
import com.kiri.server.domain.pick.repository.KiriPickRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminPickService {

    private final KiriPickRepository pickRepository;
    private final MissionRepository missionRepository;
    private final AdminAuditLogger auditLogger;
    private final EntityManager em;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PickDTO.PickItem> list() {
        LocalDateTime now = LocalDateTime.now(clock);
        return pickRepository.findAllWithMission().stream().map(pick -> toItem(pick, now)).toList();
    }

    @Transactional(readOnly = true)
    public PickDTO.PickItem get(Long pickId) {
        return toItem(find(pickId), LocalDateTime.now(clock));
    }

    @Transactional
    public PickDTO.PickItem create(Long adminId, PickDTO.PickRequest request) {
        validate(request);
        Mission mission = findPickableMission(request.missionId());
        KiriPick pick = pickRepository.save(KiriPick.builder()
                .mission(mission)
                .headline(request.headline().strip())
                .description(request.description())
                .imageUrl(blankToNull(request.imageUrl()))
                .displayOrder(request.displayOrder() == null ? 0 : request.displayOrder())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .active(request.active() == null || request.active())
                .pickType(request.pickType() == null ? PickType.CURATION : request.pickType())
                .partnerName(request.partnerName())
                .createdBy(adminId)
                .build());
        auditLogger.log(adminId, AuditAction.CREATE, AuditTargetType.KIRI_PICK, pick.getId(), null, null, request);
        return toItem(pick, LocalDateTime.now(clock));
    }

    @Transactional
    public PickDTO.PickItem update(Long adminId, Long pickId, PickDTO.PickRequest request) {
        validate(request);
        KiriPick pick = find(pickId);
        PickDTO.PickItem before = toItem(pick, LocalDateTime.now(clock));
        Mission mission = pick.getMission().getId().equals(request.missionId())
                ? pick.getMission()
                : findPickableMission(request.missionId());
        pick.update(mission, request.headline().strip(), request.description(), blankToNull(request.imageUrl()),
                request.displayOrder() == null ? pick.getDisplayOrder() : request.displayOrder(),
                request.startAt(), request.endAt(), request.active() == null || request.active(),
                request.pickType() == null ? pick.getPickType() : request.pickType(), request.partnerName());
        auditLogger.log(adminId, AuditAction.UPDATE, AuditTargetType.KIRI_PICK, pickId, null, before, request);
        return toItem(pick, LocalDateTime.now(clock));
    }

    // 전달한 순서대로 display_order를 0부터 다시 매긴다.
    @Transactional
    public List<PickDTO.PickItem> reorder(Long adminId, PickDTO.OrderRequest request) {
        List<Long> ids = request.kiriPickIds();
        if (ids == null || ids.isEmpty() || new HashSet<>(ids).size() != ids.size()) {
            throw new AdminException(AdminErrorCode.INVALID_REQUEST);
        }
        for (int i = 0; i < ids.size(); i++) {
            find(ids.get(i)).reorder(i);
        }
        auditLogger.log(adminId, AuditAction.UPDATE, AuditTargetType.KIRI_PICK, null, null, null, Map.of("order", ids));
        return list();
    }

    @Transactional(readOnly = true)
    public PickDTO.PickStats stats(Long pickId) {
        find(pickId);
        long saves = em.createQuery("""
                        SELECT COUNT(b) FROM BucketItem b
                         WHERE b.sourceKiriPickId = :id AND b.family.isTest = FALSE
                        """, Long.class)
                .setParameter("id", pickId).getSingleResult();
        Object[] schedules = em.createQuery("""
                        SELECT COUNT(s), SUM(CASE WHEN s.status = com.kiri.server.domain.schedule.enums.ScheduleStatus.COMPLETED THEN 1 ELSE 0 END)
                          FROM Schedule s JOIN s.bucketItem b
                         WHERE b.sourceKiriPickId = :id AND s.family.isTest = FALSE
                        """, Object[].class)
                .setParameter("id", pickId).getSingleResult();
        Object[] reviews = em.createQuery("""
                        SELECT COUNT(r), AVG(r.starRating),
                               AVG(CASE WHEN r.isRejoinIntended = TRUE THEN 1.0 ELSE 0.0 END)
                          FROM Review r JOIN r.schedule s JOIN s.bucketItem b
                         WHERE b.sourceKiriPickId = :id AND s.family.isTest = FALSE
                        """, Object[].class)
                .setParameter("id", pickId).getSingleResult();
        long reviewCount = ((Number) reviews[0]).longValue();
        return new PickDTO.PickStats(pickId, null, null, saves,
                ((Number) schedules[0]).longValue(),
                schedules[1] == null ? 0 : ((Number) schedules[1]).longValue(),
                reviewCount == 0 ? null : round(((Number) reviews[1]).doubleValue()),
                reviewCount == 0 ? null : round(((Number) reviews[2]).doubleValue()),
                reviewCount);
    }

    private static PickDTO.Exposure exposure(KiriPick pick, LocalDateTime now) {
        if (!pick.isActive()) {
            return PickDTO.Exposure.INACTIVE;
        }
        if (now.isBefore(pick.getStartAt())) {
            return PickDTO.Exposure.SCHEDULED;
        }
        if (!now.isBefore(pick.getEndAt())) {
            return PickDTO.Exposure.EXPIRED;
        }
        return pick.getMission().getStatus() == MissionStatus.ACTIVE
                ? PickDTO.Exposure.EXPOSED : PickDTO.Exposure.MISSION_NOT_ACTIVE;
    }

    private PickDTO.PickItem toItem(KiriPick pick, LocalDateTime now) {
        Mission mission = pick.getMission();
        return new PickDTO.PickItem(pick.getId(), mission.getId(), mission.getTitle(), mission.getStatus(),
                mission.getPlace().getName(), pick.getHeadline(), pick.getDescription(), pick.displayImageUrl(),
                pick.getDisplayOrder(), pick.getStartAt(), pick.getEndAt(), pick.isActive(), pick.getPickType(),
                pick.getPartnerName(), exposure(pick, now), pick.getCreatedAt(), pick.getUpdatedAt());
    }

    private static void validate(PickDTO.PickRequest request) {
        if (!request.endAt().isAfter(request.startAt())) {
            throw new AdminException(AdminErrorCode.INVALID_REQUEST);
        }
    }

    // 종료·취소·비공개 미션은 끼리 픽으로 편성할 수 없다.
    private Mission findPickableMission(Long missionId) {
        Mission mission = missionRepository.findWithPlaceById(missionId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.TARGET_NOT_FOUND));
        if (mission.getStatus() != MissionStatus.ACTIVE) {
            throw new AdminException(AdminErrorCode.INVALID_STATUS);
        }
        return mission;
    }

    private KiriPick find(Long pickId) {
        return pickRepository.findById(pickId).orElseThrow(() -> new AdminException(AdminErrorCode.TARGET_NOT_FOUND));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
