package com.kiri.server.admin;

import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.bucket.enums.BucketStatus;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.enums.FamilyRole;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.place.entity.Place;
import com.kiri.server.domain.place.enums.PlaceType;
import com.kiri.server.domain.review.entity.Review;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

// 관리자 통합 테스트용 엔티티 생성 도우미. 호출한 테스트의 트랜잭션 안에서 저장한다.
@Component
public class AdminFixtures {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private final EntityManager em;

    public AdminFixtures(EntityManager em) {
        this.em = em;
    }

    public Member member(String nickname) {
        Member member = Member.builder().nickname(nickname + SEQ.incrementAndGet()).email(nickname + "@kiri.test").build();
        em.persist(member);
        return member;
    }

    public Family family(String name, Member owner) {
        Family family = Family.builder().name(name + SEQ.incrementAndGet()).owner(owner).regionCode("11200").build();
        em.persist(family);
        join(owner, family, FamilyRole.MOTHER, LocalDateTime.of(2026, 10, 1, 10, 0));
        return family;
    }

    public Member join(Member member, Family family, FamilyRole role, LocalDateTime joinedAt) {
        em.createQuery("UPDATE Member m SET m.family = :f, m.familyRole = :r, m.familyJoinedAt = :t WHERE m.id = :id")
                .setParameter("f", family).setParameter("r", role).setParameter("t", joinedAt)
                .setParameter("id", member.getId()).executeUpdate();
        em.flush();
        em.refresh(member);
        return member;
    }

    public Place place(String name) {
        Place place = Place.builder().name(name).placeType(PlaceType.CULTURE_FACILITY).regionCode("11200")
                .latitude(new BigDecimal("37.5444000")).longitude(new BigDecimal("127.0374000")).build();
        em.persist(place);
        return place;
    }

    public Mission mission(Place place, String title, MissionStatus status) {
        Mission mission = Mission.builder().place(place).title(title).description("설명")
                .category(Category.EXHIBITION).missionType(MissionType.PERMANENT).status(status).build();
        em.persist(mission);
        return mission;
    }

    public BucketItem bucket(Family family, Member member, Mission mission, BucketStatus status) {
        BucketItem bucket = BucketItem.builder().family(family).member(member).mission(mission).status(status)
                .savedAt(LocalDateTime.of(2026, 10, 2, 10, 0)).build();
        em.persist(bucket);
        return bucket;
    }

    public Schedule schedule(Family family, BucketItem bucket, Member proposer, LocalDate date, ScheduleStatus status,
                             LocalDateTime completedAt) {
        Schedule schedule = Schedule.builder().family(family).bucketItem(bucket).proposer(proposer)
                .scheduleDate(date).scheduleTime(LocalTime.of(14, 0)).status(status).completedAt(completedAt).build();
        em.persist(schedule);
        return schedule;
    }

    public ScheduleParticipant participant(Schedule schedule, Member member, ParticipantStatus status) {
        ScheduleParticipant participant = ScheduleParticipant.of(schedule, member, status, LocalDateTime.of(2026, 10, 3, 10, 0));
        em.persist(participant);
        return participant;
    }

    public Review review(Schedule schedule, ScheduleParticipant participant, int star, boolean rejoin) {
        Review review = Review.builder().schedule(schedule).scheduleParticipant(participant).content("좋았어요")
                .starRating(star).isRejoinIntended(rejoin).build();
        em.persist(review);
        return review;
    }

    public void flushAndClear() {
        em.flush();
        em.clear();
    }
}
