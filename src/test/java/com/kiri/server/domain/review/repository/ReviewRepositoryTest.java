package com.kiri.server.domain.review.repository;

import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.bucket.enums.BucketStatus;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.place.entity.Place;
import com.kiri.server.domain.place.enums.PlaceType;
import com.kiri.server.domain.review.entity.Review;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.global.config.JpaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class ReviewRepositoryTest {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private TestEntityManager em;

    private Place place;

    @Test
    @DisplayName("한 미션에 달린 별점을 가족 구분 없이 전체 사용자 기준으로 평균과 개수를 집계한다.")
    void findMissionRating_global() {
        place = persistPlace();
        Mission rated = persistMission("미술관 관람하기");
        Member me = persistMember("별점나");
        Member neighbor = persistMember("별점이웃");
        Family familyA = persistFamily("별점가족A", me);
        Family familyB = persistFamily("별점가족B", neighbor);

        BucketItem bucketA = persistBucketItem(familyA, me, rated);
        BucketItem bucketB = persistBucketItem(familyB, neighbor, rated);
        persistReview(bucketA, me, 5);
        persistReview(bucketB, neighbor, 4);
        em.flush();
        em.clear();

        ReviewRepository.MissionRating rating = reviewRepository.findMissionRating(rated.getId());

        assertThat(rating.getReviewCount()).isEqualTo(2);
        assertThat(rating.getAverageRating()).isEqualTo(4.5);
    }

    @Test
    @DisplayName("별점이 없는 미션은 개수 0, 평균은 null로 집계한다.")
    void findMissionRating_noReview() {
        place = persistPlace();
        Mission unrated = persistMission("아무도 안 간 미션");
        em.flush();
        em.clear();

        ReviewRepository.MissionRating rating = reviewRepository.findMissionRating(unrated.getId());

        assertThat(rating.getReviewCount()).isZero();
        assertThat(rating.getAverageRating()).isNull();
    }

    private Place persistPlace() {
        return em.persist(Place.builder()
                .name("여의도 한강공원")
                .placeType(PlaceType.TOURIST_SPOT)
                .latitude(new BigDecimal("37.5284000"))
                .longitude(new BigDecimal("126.9326000"))
                .build());
    }

    private Mission persistMission(String title) {
        return em.persist(Mission.builder()
                .place(place)
                .title(title)
                .description("설명")
                .category(Category.EXHIBITION)
                .missionType(MissionType.PERMANENT)
                .build());
    }

    private Member persistMember(String nickname) {
        return em.persist(Member.builder().nickname(nickname).build());
    }

    private Family persistFamily(String name, Member owner) {
        return em.persist(Family.builder().name(name).owner(owner).build());
    }

    private BucketItem persistBucketItem(Family family, Member member, Mission mission) {
        return em.persist(BucketItem.builder()
                .family(family)
                .member(member)
                .mission(mission)
                .status(BucketStatus.COMPLETED)
                .savedAt(LocalDateTime.of(2026, 10, 1, 10, 0))
                .build());
    }

    private void persistReview(BucketItem bucketItem, Member member, int starRating) {
        Schedule schedule = em.persist(Schedule.builder()
                .family(bucketItem.getFamily())
                .bucketItem(bucketItem)
                .scheduleDate(LocalDate.of(2026, 10, 10))
                .status(ScheduleStatus.COMPLETED)
                .build());
        ScheduleParticipant participant = em.persist(ScheduleParticipant.builder()
                .schedule(schedule)
                .member(member)
                .status(ParticipantStatus.ACCEPTED)
                .reconfirmRequired(false)
                .build());
        em.persist(Review.builder()
                .schedule(schedule)
                .scheduleParticipant(participant)
                .content("기록")
                .starRating(starRating)
                .isRejoinIntended(true)
                .build());
    }
}
