package com.kiri.server.domain.bucket.repository;

import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.bucket.enums.BucketStatus;
import com.kiri.server.domain.bucket.repository.BucketItemRepository.SaverBucketCount;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.family.repository.FamilyRepository;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.mission.repository.MissionRepository;
import com.kiri.server.domain.place.entity.Place;
import com.kiri.server.domain.place.enums.PlaceType;
import com.kiri.server.domain.place.repository.PlaceRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@Transactional
class BucketItemRepositoryTest {

    private static final LocalDateTime SAVED_AT = LocalDateTime.of(2026, 10, 27, 19, 0);

    @Autowired
    private BucketItemRepository bucketItemRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private MissionRepository missionRepository;

    @Autowired
    private EntityManager entityManager;

    private Member dad;
    private Member daughter;
    private Family family;
    private Mission festival;
    private Mission museum;

    @BeforeEach
    void setUp() {
        dad = memberRepository.save(Member.builder().nickname("버킷아빠").build());
        daughter = memberRepository.save(Member.builder().nickname("버킷딸").build());
        family = familyRepository.save(Family.builder().owner(dad).name("버킷테스트가족").build());
        dad.joinFamily(family);
        daughter.joinFamily(family);

        Place place = placeRepository.save(Place.builder()
                .name("여의도 한강공원")
                .placeType(PlaceType.TOURIST_SPOT)
                .latitude(new BigDecimal("37.5284000"))
                .longitude(new BigDecimal("126.9326000"))
                .build());
        festival = missionRepository.save(mission(place, "한강 뚜벅뚜벅 축제 방문하기"));
        museum = missionRepository.save(mission(place, "미술관 관람하기"));
        flushAndClear();
    }

    @Test
    @DisplayName("SAVED·SCHEDULED 상태로 저장한 미션만 저장한 것으로 본다.")
    void existsActive() {
        save(dad, festival, BucketStatus.SAVED);
        save(dad, museum, BucketStatus.COMPLETED);
        flushAndClear();

        assertThat(exists(dad, festival)).isTrue();
        assertThat(exists(dad, museum)).isFalse();
        assertThat(exists(daughter, festival)).isFalse();
    }

    @Test
    @DisplayName("SCHEDULED 상태도 저장한 것으로 본다.")
    void existsScheduled() {
        save(dad, festival, BucketStatus.SCHEDULED);
        flushAndClear();

        assertThat(exists(dad, festival)).isTrue();
    }

    @Test
    @DisplayName("같은 회원이 같은 미션을 SAVED로 두 번 저장하면 유니크 제약에 막힌다.")
    void duplicateActive() {
        save(dad, festival, BucketStatus.SAVED);
        flushAndClear();

        assertThatThrownBy(() -> save(dad, festival, BucketStatus.SAVED))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("SCHEDULED로 연결된 미션을 다시 SAVED로 저장해도 유니크 제약에 막힌다.")
    void duplicateScheduled() {
        save(dad, festival, BucketStatus.SCHEDULED);
        flushAndClear();

        assertThatThrownBy(() -> save(dad, festival, BucketStatus.SAVED))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("완료된 뒤에는 같은 미션을 다시 저장할 수 있고, 같은 가족의 다른 회원도 각각 저장할 수 있다.")
    void resaveAfterCompletedAndOtherMember() {
        save(dad, festival, BucketStatus.COMPLETED);
        flushAndClear();

        assertThatCode(() -> {
            save(dad, festival, BucketStatus.SAVED);
            save(daughter, festival, BucketStatus.SAVED);
            flushAndClear();
        }).doesNotThrowAnyException();
        assertThat(exists(dad, festival)).isTrue();
        assertThat(exists(daughter, festival)).isTrue();
    }

    @Test
    @DisplayName("저장한 회원 행이 삭제되면 항목은 남고 저장자만 비워진다.")
    void memberDeleted() {
        Long itemId = save(daughter, festival, BucketStatus.SAVED).getId();
        flushAndClear();

        entityManager.createNativeQuery("DELETE FROM member WHERE member_id = :id")
                .setParameter("id", daughter.getId())
                .executeUpdate();
        entityManager.clear();

        BucketItem item = bucketItemRepository.findById(itemId).orElseThrow();
        assertThat(item.getMember()).isNull();
        assertThat(item.getFamily().getId()).isEqualTo(family.getId());
    }

    @Test
    @DisplayName("가족 항목을 상태와 상관없이 최신 저장순으로 조회하고, 미션과 저장자를 함께 가져온다. 다른 가족 항목은 제외한다.")
    void findFamilyPage() {
        BucketItem completed = save(dad, festival, BucketStatus.COMPLETED);
        BucketItem scheduled = save(daughter, festival, BucketStatus.SCHEDULED);
        BucketItem saved = save(dad, museum, BucketStatus.SAVED);
        saveOtherFamilyItem();
        flushAndClear();

        List<BucketItem> items = bucketItemRepository.findByFamilyIdAndIdLessThanOrderByIdDesc(
                family.getId(), Long.MAX_VALUE, PageRequest.of(0, 10));

        assertThat(items).extracting(BucketItem::getId)
                .containsExactly(saved.getId(), scheduled.getId(), completed.getId());
        assertThat(Hibernate.isInitialized(items.getFirst().getMission())).isTrue();
        assertThat(Hibernate.isInitialized(items.getFirst().getMember())).isTrue();
    }

    @Test
    @DisplayName("커서보다 작은 ID의 항목만 개수 제한만큼 조회한다.")
    void findFamilyPage_cursor() {
        BucketItem first = save(dad, festival, BucketStatus.SAVED);
        BucketItem second = save(dad, museum, BucketStatus.SAVED);
        BucketItem third = save(daughter, festival, BucketStatus.SAVED);
        flushAndClear();

        List<BucketItem> firstPage = bucketItemRepository.findByFamilyIdAndIdLessThanOrderByIdDesc(
                family.getId(), Long.MAX_VALUE, PageRequest.of(0, 2));
        List<BucketItem> nextPage = bucketItemRepository.findByFamilyIdAndIdLessThanOrderByIdDesc(
                family.getId(), firstPage.getLast().getId(), PageRequest.of(0, 2));

        assertThat(firstPage).extracting(BucketItem::getId).containsExactly(third.getId(), second.getId());
        assertThat(nextPage).extracting(BucketItem::getId).containsExactly(first.getId());
    }

    @Test
    @DisplayName("저장자를 지정하면 그 회원이 저장한 가족 항목만 조회한다.")
    void findFamilyMemberPage() {
        save(dad, festival, BucketStatus.SAVED);
        BucketItem daughterItem = save(daughter, museum, BucketStatus.SAVED);
        flushAndClear();

        List<BucketItem> items = bucketItemRepository.findByFamilyIdAndMemberIdAndIdLessThanOrderByIdDesc(
                family.getId(), daughter.getId(), Long.MAX_VALUE, PageRequest.of(0, 10));

        assertThat(items).extracting(BucketItem::getId).containsExactly(daughterItem.getId());
    }

    @Test
    @DisplayName("저장자가 탈퇴해 비어 있는 항목도 가족 목록에는 포함하지만 저장자별 개수에서는 뺀다.")
    void withdrawnSaver() {
        save(dad, festival, BucketStatus.SAVED);
        save(dad, museum, BucketStatus.COMPLETED);
        BucketItem daughterItem = save(daughter, festival, BucketStatus.SAVED);
        saveOtherFamilyItem();
        flushAndClear();
        deleteMember(daughter);

        List<BucketItem> items = bucketItemRepository.findByFamilyIdAndIdLessThanOrderByIdDesc(
                family.getId(), Long.MAX_VALUE, PageRequest.of(0, 10));
        List<SaverBucketCount> counts = bucketItemRepository.findSaverCounts(family.getId());

        assertThat(items).hasSize(3);
        assertThat(items.getFirst().getId()).isEqualTo(daughterItem.getId());
        assertThat(items.getFirst().getMember()).isNull();
        assertThat(counts).extracting(SaverBucketCount::getMemberId, SaverBucketCount::getBucketCount)
                .containsExactly(tuple(dad.getId(), 2L));
    }

    @Test
    @DisplayName("삭제할 항목을 행 잠금과 함께 조회하고, 없는 ID면 빈 값을 반환한다.")
    void findByIdForUpdate() {
        Long itemId = save(dad, festival, BucketStatus.SAVED).getId();
        flushAndClear();

        BucketItem locked = bucketItemRepository.findByIdForUpdate(itemId).orElseThrow();

        assertThat(locked.getId()).isEqualTo(itemId);
        assertThat(bucketItemRepository.findByIdForUpdate(-1L)).isEmpty();
    }

    @Test
    @DisplayName("SAVED 항목을 삭제하면 같은 회원이 같은 미션을 다시 저장할 수 있다.")
    void resaveAfterDelete() {
        Long itemId = save(dad, festival, BucketStatus.SAVED).getId();
        flushAndClear();

        bucketItemRepository.delete(bucketItemRepository.findByIdForUpdate(itemId).orElseThrow());
        flushAndClear();

        assertThat(bucketItemRepository.findById(itemId)).isEmpty();
        assertThat(exists(dad, festival)).isFalse();
        assertThatCode(() -> save(dad, festival, BucketStatus.SAVED)).doesNotThrowAnyException();
        assertThat(exists(dad, festival)).isTrue();
    }

    private BucketItem save(Member member, Mission mission, BucketStatus status) {
        return bucketItemRepository.saveAndFlush(BucketItem.builder()
                .family(family)
                .member(member)
                .mission(mission)
                .status(status)
                .savedAt(SAVED_AT)
                .build());
    }

    private void saveOtherFamilyItem() {
        Member neighbor = memberRepository.save(Member.builder().nickname("버킷옆집아빠").build());
        Family otherFamily = familyRepository.save(Family.builder().owner(neighbor).name("버킷옆집가족").build());
        neighbor.joinFamily(otherFamily);
        bucketItemRepository.saveAndFlush(BucketItem.builder()
                .family(otherFamily)
                .member(neighbor)
                .mission(festival)
                .savedAt(SAVED_AT)
                .build());
    }

    private void deleteMember(Member member) {
        entityManager.createNativeQuery("DELETE FROM member WHERE member_id = :id")
                .setParameter("id", member.getId())
                .executeUpdate();
        entityManager.clear();
    }

    private boolean exists(Member member, Mission mission) {
        return bucketItemRepository.existsByMemberIdAndMissionIdAndStatusIn(
                member.getId(), mission.getId(), BucketStatus.ACTIVE_STATUSES);
    }

    private Mission mission(Place place, String title) {
        return Mission.builder()
                .place(place)
                .title(title)
                .description("설명")
                .category(Category.POPUP)
                .missionType(MissionType.PERMANENT)
                .build();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
