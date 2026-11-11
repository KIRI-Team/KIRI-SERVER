package com.kiri.server.admin.lookup.service;

import com.kiri.server.admin.audit.enums.AuditAction;
import com.kiri.server.admin.audit.enums.AuditTargetType;
import com.kiri.server.admin.audit.service.AdminAuditLogger;
import com.kiri.server.admin.common.AdminErrorCode;
import com.kiri.server.admin.common.AdminException;
import com.kiri.server.admin.common.AdminPageRequest;
import com.kiri.server.admin.common.AdminPageResponse;
import com.kiri.server.admin.common.Reasons;
import com.kiri.server.admin.lookup.dto.LookupDTO;
import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.bucket.enums.BucketStatus;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.image.service.ImageUrlService;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.review.entity.Review;
import com.kiri.server.domain.review.entity.ReviewPhoto;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.domain.term.entity.MemberTerm;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminLookupService {

    private static final int RECENT_ANSWER_LIMIT = 30;
    static final String WARNING_REVIEW_WITHOUT_COMPLETION = "REVIEW_WITHOUT_COMPLETION";
    static final String WARNING_BUCKET_NOT_COMPLETED = "COMPLETED_BUCKET_MISMATCH";

    private final EntityManager em;
    private final AdminAuditLogger adminAuditLogger;
    private final ImageUrlService imageUrlService;
    private final Clock clock;

    // ===== 회원 =====

    @Transactional(readOnly = true)
    public AdminPageResponse<LookupDTO.MemberItem> searchMembers(LookupDTO.MemberCondition c) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> params = new HashMap<>();
        if (c.memberId() != null) {
            where.append(" AND m.id = :memberId");
            params.put("memberId", c.memberId());
        }
        if (c.keyword() != null && !c.keyword().isBlank()) {
            where.append(" AND (LOWER(m.nickname) LIKE :kw OR LOWER(m.email) LIKE :kw)");
            params.put("kw", "%" + c.keyword().strip().toLowerCase() + "%");
        }
        if (c.state() == LookupDTO.MemberState.ACTIVE) {
            where.append(" AND m.deletedAt IS NULL");
        } else if (c.state() == LookupDTO.MemberState.WITHDRAWN) {
            where.append(" AND m.deletedAt IS NOT NULL");
        }
        if (c.hasFamily() != null) {
            where.append(c.hasFamily() ? " AND m.family IS NOT NULL" : " AND m.family IS NULL");
        }
        if (c.familyRole() != null) {
            where.append(" AND m.familyRole = :role");
            params.put("role", c.familyRole());
        }
        if (c.isTest() != null) {
            where.append(" AND m.isTest = :isTest");
            params.put("isTest", c.isTest());
        }
        Pageable pageable = AdminPageRequest.of(c.page(), c.size());
        TypedQuery<Member> query = em.createQuery(
                "SELECT m FROM Member m LEFT JOIN FETCH m.family" + where + " ORDER BY m.id DESC", Member.class);
        TypedQuery<Long> count = em.createQuery("SELECT COUNT(m) FROM Member m" + where, Long.class);
        params.forEach((k, v) -> {
            query.setParameter(k, v);
            count.setParameter(k, v);
        });
        List<LookupDTO.MemberItem> content = query.setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList().stream().map(this::toMemberItem).toList();
        return AdminPageResponse.of(content, pageable.getPageNumber(), pageable.getPageSize(), count.getSingleResult());
    }

    @Transactional(readOnly = true)
    public LookupDTO.MemberDetail getMember(Long memberId) {
        Member member = findMember(memberId);
        boolean tasteConfigured = count("SELECT COUNT(t) FROM MemberTaste t WHERE t.member.id = :id", memberId) > 0;
        List<LookupDTO.TermAgreement> terms = em.createQuery(
                        "SELECT mt FROM MemberTerm mt JOIN FETCH mt.term WHERE mt.member.id = :id ORDER BY mt.term.id",
                        MemberTerm.class)
                .setParameter("id", memberId).getResultList().stream()
                .map(mt -> new LookupDTO.TermAgreement(mt.getTerm().getTitle(), mt.getTermVersion(),
                        mt.getAgreedAt() != null, mt.getAgreedAt()))
                .toList();
        LookupDTO.ActivitySummary activity = new LookupDTO.ActivitySummary(
                count("SELECT COUNT(b) FROM BucketItem b WHERE b.member.id = :id", memberId),
                count("SELECT COUNT(s) FROM Schedule s WHERE s.proposer.id = :id", memberId),
                count("SELECT COUNT(p) FROM ScheduleParticipant p WHERE p.member.id = :id", memberId),
                count("SELECT COUNT(r) FROM Review r WHERE r.scheduleParticipant.member.id = :id", memberId),
                count("SELECT COUNT(a) FROM MemberAnswer a WHERE a.member.id = :id", memberId));
        return new LookupDTO.MemberDetail(toMemberItem(member), member.getFamilyJoinedAt(), tasteConfigured, terms, activity);
    }

    @Transactional
    public LookupDTO.MemberItem markMemberTest(Long adminId, Long memberId, boolean isTest) {
        Member member = findMember(memberId);
        boolean before = Boolean.TRUE.equals(member.getIsTest());
        member.markTest(isTest);
        adminAuditLogger.log(adminId, AuditAction.UPDATE, AuditTargetType.MEMBER, memberId, null,
                Map.of("isTest", before), Map.of("isTest", isTest));
        return toMemberItem(member);
    }

    // ===== 가족 =====

    @Transactional(readOnly = true)
    public AdminPageResponse<LookupDTO.FamilyItem> searchFamilies(LookupDTO.FamilyCondition c) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> params = new HashMap<>();
        if (c.familyId() != null) {
            where.append(" AND f.id = :familyId");
            params.put("familyId", c.familyId());
        }
        if (c.keyword() != null && !c.keyword().isBlank()) {
            where.append(" AND LOWER(f.name) LIKE :kw");
            params.put("kw", "%" + c.keyword().strip().toLowerCase() + "%");
        }
        if (c.isTest() != null) {
            where.append(" AND f.isTest = :isTest");
            params.put("isTest", c.isTest());
        }
        Pageable pageable = AdminPageRequest.of(c.page(), c.size());
        TypedQuery<Family> query = em.createQuery("SELECT f FROM Family f" + where + " ORDER BY f.id DESC", Family.class);
        TypedQuery<Long> count = em.createQuery("SELECT COUNT(f) FROM Family f" + where, Long.class);
        params.forEach((k, v) -> {
            query.setParameter(k, v);
            count.setParameter(k, v);
        });
        List<Family> families = query.setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize()).getResultList();
        Map<Long, Long> memberCounts = memberCounts(families.stream().map(Family::getId).toList());
        List<LookupDTO.FamilyItem> content = families.stream()
                .map(f -> toFamilyItem(f, memberCounts.getOrDefault(f.getId(), 0L)))
                .toList();
        return AdminPageResponse.of(content, pageable.getPageNumber(), pageable.getPageSize(), count.getSingleResult());
    }

    @Transactional(readOnly = true)
    public LookupDTO.FamilyDetail getFamily(Long familyId) {
        Family family = findFamily(familyId);
        List<Member> members = em.createQuery(
                        "SELECT m FROM Member m WHERE m.family.id = :id ORDER BY m.familyJoinedAt", Member.class)
                .setParameter("id", familyId).getResultList();
        Long ownerId = family.getOwner() == null ? null : family.getOwner().getId();
        List<LookupDTO.FamilyMember> memberItems = members.stream()
                .map(m -> new LookupDTO.FamilyMember(m.getId(), m.getDisplayName(), m.getFamilyRole(),
                        m.getId().equals(ownerId), Boolean.TRUE.equals(m.getIsTest()), m.isWithdrawn(),
                        m.getFamilyJoinedAt()))
                .toList();
        LocalDateTime now = LocalDateTime.now(clock);
        LookupDTO.InviteStatus invite = new LookupDTO.InviteStatus(
                family.getInviteExpiresAt() != null && family.getInviteExpiresAt().isAfter(now),
                family.getInviteExpiresAt());

        Map<BucketStatus, Long> bucketCounts = new EnumMap<>(BucketStatus.class);
        em.createQuery("SELECT b.status, COUNT(b) FROM BucketItem b WHERE b.family.id = :id GROUP BY b.status", Object[].class)
                .setParameter("id", familyId).getResultList()
                .forEach(row -> bucketCounts.put((BucketStatus) row[0], (Long) row[1]));
        Map<ScheduleStatus, Long> scheduleCounts = new EnumMap<>(ScheduleStatus.class);
        em.createQuery("SELECT s.status, COUNT(s) FROM Schedule s WHERE s.family.id = :id GROUP BY s.status", Object[].class)
                .setParameter("id", familyId).getResultList()
                .forEach(row -> scheduleCounts.put((ScheduleStatus) row[0], (Long) row[1]));
        long reviewCount = count("SELECT COUNT(r) FROM Review r WHERE r.schedule.family.id = :id", familyId);

        return new LookupDTO.FamilyDetail(toFamilyItem(family, members.size()), memberItems, invite,
                bucketCounts, scheduleCounts, reviewCount);
    }

    @Transactional
    public LookupDTO.FamilyItem markFamilyTest(Long adminId, Long familyId, boolean isTest) {
        Family family = findFamily(familyId);
        boolean before = Boolean.TRUE.equals(family.getIsTest());
        family.markTest(isTest);
        adminAuditLogger.log(adminId, AuditAction.UPDATE, AuditTargetType.FAMILY, familyId, null,
                Map.of("isTest", before), Map.of("isTest", isTest));
        return toFamilyItem(family, memberCounts(List.of(familyId)).getOrDefault(familyId, 0L));
    }

    // 정밀 위치·답변 원문·리뷰 본문과 사진. 사유를 받고 열람 이력을 남긴다.
    @Transactional
    public LookupDTO.SensitiveFamily viewSensitive(Long adminId, Long familyId, String reason) {
        String validReason = Reasons.require(reason);
        Family family = findFamily(familyId);

        List<LookupDTO.Answer> answers = em.createQuery("""
                        SELECT a.member.id, fq.questionDate, q.content, a.content
                          FROM MemberAnswer a JOIN a.familyQuestion fq JOIN fq.question q
                         WHERE fq.family.id = :id
                         ORDER BY fq.questionDate DESC, a.id DESC
                        """, Object[].class)
                .setParameter("id", familyId).setMaxResults(RECENT_ANSWER_LIMIT).getResultList().stream()
                .map(r -> new LookupDTO.Answer((Long) r[0], (java.time.LocalDate) r[1], (String) r[2], (String) r[3]))
                .toList();

        List<Review> reviews = em.createQuery("""
                        SELECT r FROM Review r JOIN FETCH r.schedule s LEFT JOIN FETCH r.scheduleParticipant p
                         WHERE s.family.id = :id ORDER BY r.id DESC
                        """, Review.class)
                .setParameter("id", familyId).getResultList();
        Map<Long, List<String>> photos = new HashMap<>();
        if (!reviews.isEmpty()) {
            em.createQuery("SELECT p FROM ReviewPhoto p WHERE p.review IN :reviews ORDER BY p.displayOrder", ReviewPhoto.class)
                    .setParameter("reviews", reviews).getResultList()
                    .forEach(p -> photos.computeIfAbsent(p.getReview().getId(), k -> new ArrayList<>())
                            .add(imageUrlService.toUrl(p.getImageKey())));
        }
        List<LookupDTO.ReviewContent> reviewContents = reviews.stream()
                .map(r -> new LookupDTO.ReviewContent(r.getId(), r.getSchedule().getId(),
                        r.getScheduleParticipant() == null || r.getScheduleParticipant().getMember() == null
                                ? null : r.getScheduleParticipant().getMember().getId(),
                        r.getContent(), photos.getOrDefault(r.getId(), List.of())))
                .toList();

        adminAuditLogger.log(adminId, AuditAction.VIEW_SENSITIVE, AuditTargetType.FAMILY, familyId, validReason, null, null);
        return new LookupDTO.SensitiveFamily(family.getId(), family.getPostalCode(), family.getAddress(),
                family.getDetailAddress(), family.getLatitude(), family.getLongitude(), answers, reviewContents);
    }

    // ===== 일정 =====

    @Transactional(readOnly = true)
    public AdminPageResponse<LookupDTO.ScheduleItem> searchFamilySchedules(Long familyId, LookupDTO.ScheduleSearchCondition c) {
        findFamily(familyId);
        String where = " WHERE s.family.id = :id" + (c.status() != null ? " AND s.status = :status" : "");
        Pageable pageable = AdminPageRequest.of(c.page(), c.size());
        TypedQuery<Schedule> query = em.createQuery("""
                SELECT s FROM Schedule s
                  LEFT JOIN FETCH s.bucketItem b LEFT JOIN FETCH b.mission LEFT JOIN FETCH s.proposer
                """ + where + " ORDER BY s.id DESC", Schedule.class);
        TypedQuery<Long> count = em.createQuery("SELECT COUNT(s) FROM Schedule s" + where, Long.class);
        query.setParameter("id", familyId);
        count.setParameter("id", familyId);
        if (c.status() != null) {
            query.setParameter("status", c.status());
            count.setParameter("status", c.status());
        }
        List<LookupDTO.ScheduleItem> content = query.setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize()).getResultList().stream().map(this::toScheduleItem).toList();
        return AdminPageResponse.of(content, pageable.getPageNumber(), pageable.getPageSize(), count.getSingleResult());
    }

    @Transactional(readOnly = true)
    public LookupDTO.ScheduleDetail getSchedule(Long scheduleId) {
        Schedule schedule = em.createQuery("""
                        SELECT s FROM Schedule s
                          LEFT JOIN FETCH s.bucketItem b LEFT JOIN FETCH b.mission LEFT JOIN FETCH s.proposer
                         WHERE s.id = :id
                        """, Schedule.class)
                .setParameter("id", scheduleId).getResultStream().findFirst()
                .orElseThrow(() -> new AdminException(AdminErrorCode.TARGET_NOT_FOUND));

        List<LookupDTO.Participant> participants = em.createQuery(
                        "SELECT p FROM ScheduleParticipant p LEFT JOIN FETCH p.member WHERE p.schedule.id = :id ORDER BY p.id",
                        ScheduleParticipant.class)
                .setParameter("id", scheduleId).getResultList().stream()
                .map(p -> new LookupDTO.Participant(p.getId(),
                        p.getMember() == null ? null : p.getMember().getId(),
                        p.getMember() == null ? null : p.getMember().getDisplayName(),
                        p.getStatus(), p.getRespondedAt()))
                .toList();

        List<Review> reviews = em.createQuery(
                        "SELECT r FROM Review r LEFT JOIN FETCH r.scheduleParticipant p LEFT JOIN FETCH p.member WHERE r.schedule.id = :id ORDER BY r.id",
                        Review.class)
                .setParameter("id", scheduleId).getResultList();
        Map<Long, Long> photoCounts = new HashMap<>();
        if (!reviews.isEmpty()) {
            em.createQuery("SELECT p.review.id, COUNT(p) FROM ReviewPhoto p WHERE p.review IN :reviews GROUP BY p.review.id", Object[].class)
                    .setParameter("reviews", reviews).getResultList()
                    .forEach(row -> photoCounts.put((Long) row[0], (Long) row[1]));
        }
        List<LookupDTO.ReviewSummary> reviewSummaries = reviews.stream()
                .map(r -> new LookupDTO.ReviewSummary(r.getId(),
                        r.getScheduleParticipant() == null || r.getScheduleParticipant().getMember() == null
                                ? null : r.getScheduleParticipant().getMember().getId(),
                        r.getStarRating(), r.getIsRejoinIntended(), photoCounts.getOrDefault(r.getId(), 0L),
                        r.getCreatedAt()))
                .toList();

        BucketItem bucket = schedule.getBucketItem();
        LookupDTO.BucketInfo bucketInfo = bucket == null ? null
                : new LookupDTO.BucketInfo(bucket.getId(), bucket.getStatus(), bucket.getSavedAt(), bucket.getCompletedAt());

        return new LookupDTO.ScheduleDetail(toScheduleItem(schedule), schedule.getFamily().getId(),
                schedule.getResponseDeadline(), schedule.getStartedAt(), bucketInfo, participants, reviewSummaries,
                warnings(schedule, bucket, reviews));
    }

    // 완료 정책: 참여자 1명의 유효 리뷰가 있으면 일정과 버킷이 완료되어야 한다.
    static List<String> warnings(Schedule schedule, BucketItem bucket, List<Review> reviews) {
        List<String> warnings = new ArrayList<>();
        if (!reviews.isEmpty() && schedule.getStatus() != ScheduleStatus.COMPLETED) {
            warnings.add(WARNING_REVIEW_WITHOUT_COMPLETION);
        }
        if (schedule.getStatus() == ScheduleStatus.COMPLETED && bucket != null && bucket.getStatus() != BucketStatus.COMPLETED) {
            warnings.add(WARNING_BUCKET_NOT_COMPLETED);
        }
        return warnings;
    }

    // ===== 공통 =====

    private Member findMember(Long memberId) {
        Member member = em.find(Member.class, memberId);
        if (member == null) {
            throw new AdminException(AdminErrorCode.TARGET_NOT_FOUND);
        }
        return member;
    }

    private Family findFamily(Long familyId) {
        Family family = em.find(Family.class, familyId);
        if (family == null) {
            throw new AdminException(AdminErrorCode.TARGET_NOT_FOUND);
        }
        return family;
    }

    private long count(String jpql, Long id) {
        return em.createQuery(jpql, Long.class).setParameter("id", id).getSingleResult();
    }

    private Map<Long, Long> memberCounts(List<Long> familyIds) {
        Map<Long, Long> counts = new HashMap<>();
        if (familyIds.isEmpty()) {
            return counts;
        }
        em.createQuery("SELECT m.family.id, COUNT(m) FROM Member m WHERE m.family.id IN :ids GROUP BY m.family.id", Object[].class)
                .setParameter("ids", familyIds).getResultList()
                .forEach(row -> counts.put((Long) row[0], (Long) row[1]));
        return counts;
    }

    private LookupDTO.MemberItem toMemberItem(Member m) {
        Family family = m.getFamily();
        return new LookupDTO.MemberItem(m.getId(), m.getNickname(), m.getEmail(), m.getSocialProvider(),
                family == null ? null : family.getId(), family == null ? null : family.getName(),
                m.getFamilyRole(), Boolean.TRUE.equals(m.getIsTest()), m.isWithdrawn(), m.getCreatedAt(), m.getDeletedAt());
    }

    private LookupDTO.FamilyItem toFamilyItem(Family f, long memberCount) {
        return new LookupDTO.FamilyItem(f.getId(), f.getName(), f.getOwner() == null ? null : f.getOwner().getId(),
                memberCount, f.getRegionCode(), Boolean.TRUE.equals(f.getIsTest()), f.getCreatedAt());
    }

    private LookupDTO.ScheduleItem toScheduleItem(Schedule s) {
        BucketItem bucket = s.getBucketItem();
        Member proposer = s.getProposer();
        return new LookupDTO.ScheduleItem(s.getId(),
                bucket == null ? null : bucket.getMission().getId(),
                bucket == null ? null : bucket.getMission().getTitle(),
                s.getScheduleDate(), s.getScheduleTime(), s.getStatus(),
                proposer == null ? null : proposer.getId(),
                proposer == null ? null : proposer.getDisplayName(),
                s.getCreatedAt(), s.getCompletedAt());
    }
}
