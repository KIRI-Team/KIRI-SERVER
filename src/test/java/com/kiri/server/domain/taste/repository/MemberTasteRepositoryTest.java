package com.kiri.server.domain.taste.repository;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.family.repository.FamilyRepository;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.taste.dto.TasteReqDTO;
import com.kiri.server.domain.taste.entity.MemberTaste;
import com.kiri.server.domain.taste.enums.ActivityLevel;
import com.kiri.server.domain.taste.enums.DurationLevel;
import com.kiri.server.domain.taste.enums.PriceLevel;
import com.kiri.server.domain.taste.exception.TasteException;
import com.kiri.server.domain.taste.service.TasteService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class MemberTasteRepositoryTest {

    @Autowired
    private MemberTasteRepository memberTasteRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private TasteService tasteService;

    @Autowired
    private EntityManager entityManager;

    private Member mom;
    private Member daughter;
    private Member other;
    private Family family;

    @BeforeEach
    void setUp() {
        mom = memberRepository.save(Member.builder().nickname("취향엄마").build());
        daughter = memberRepository.save(Member.builder().nickname("취향딸").build());
        other = memberRepository.save(Member.builder().nickname("취향이웃").build());
        family = familyRepository.save(Family.builder().owner(mom).name("취향테스트가족").build());
        mom.assignFamily(family);
        daughter.assignFamily(family);
        flushAndClear();
    }

    @Test
    @DisplayName("저장한 취향을 선호 활동과 함께 조회한다.")
    void findWithCategories() {
        tasteService.saveMyTaste(mom.getId(), request(Category.FOOD_CAFE, Category.EXHIBITION));
        flushAndClear();

        MemberTaste taste = memberTasteRepository.findWithCategoriesByMemberId(mom.getId()).orElseThrow();

        assertThat(taste.getCategoryValues()).containsExactly(Category.EXHIBITION, Category.FOOD_CAFE);
        assertThat(taste.getIndoorOutdoor()).isEqualTo(IndoorOutdoor.INDOOR);
        assertThat(memberTasteRepository.findWithCategoriesByMemberId(daughter.getId())).isEmpty();
    }

    @Test
    @DisplayName("겹치는 선호 활동을 다시 저장해도 유니크 제약에 걸리지 않고 한 행만 남는다.")
    void resaveOverlappingCategories() {
        tasteService.saveMyTaste(mom.getId(), request(Category.EXHIBITION, Category.FOOD_CAFE));
        flushAndClear();

        tasteService.saveMyTaste(mom.getId(), request(Category.FOOD_CAFE, Category.POPUP));
        flushAndClear();

        MemberTaste taste = memberTasteRepository.findWithCategoriesByMemberId(mom.getId()).orElseThrow();
        assertThat(taste.getCategoryValues()).containsExactly(Category.POPUP, Category.FOOD_CAFE);
        assertThat(countCategories(taste.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("검증에 실패한 저장 요청은 기존 취향을 바꾸지 않는다.")
    void keepOnFailure() {
        tasteService.saveMyTaste(mom.getId(), request(Category.EXHIBITION));
        flushAndClear();

        assertThatThrownBy(() -> tasteService.saveMyTaste(mom.getId(),
                new TasteReqDTO.Save(List.of(Category.POPUP), IndoorOutdoor.BOTH, ActivityLevel.ACTIVE, null, null)))
                .isInstanceOf(TasteException.class);
        entityManager.clear();

        MemberTaste taste = memberTasteRepository.findWithCategoriesByMemberId(mom.getId()).orElseThrow();
        assertThat(taste.getCategoryValues()).containsExactly(Category.EXHIBITION);
        assertThat(taste.getActivityLevel()).isEqualTo(ActivityLevel.LIGHT);
    }

    @Test
    @DisplayName("가족 구성원 ID를 순서대로 찾고, 구성원들의 취향을 한 번에 가져온다.")
    void familyMembers() {
        tasteService.saveMyTaste(mom.getId(), request(Category.EXHIBITION));
        tasteService.saveMyTaste(other.getId(), request(Category.POPUP));
        flushAndClear();

        List<Long> memberIds = memberTasteRepository.findMemberIdsByFamilyId(family.getId());
        List<MemberTaste> tastes = memberTasteRepository.findAllWithCategoriesByMemberIdIn(memberIds);

        assertThat(memberIds).containsExactly(mom.getId(), daughter.getId());
        assertThat(tastes).hasSize(1);
        assertThat(tastes.get(0).getMember().getId()).isEqualTo(mom.getId());
        assertThat(tastes.get(0).getCategoryValues()).containsExactly(Category.EXHIBITION);
    }

    @Test
    @DisplayName("취향을 삭제하면 선호 활동도 함께 지워진다.")
    void deleteWithCategories() {
        tasteService.saveMyTaste(mom.getId(), request(Category.EXHIBITION, Category.POPUP));
        flushAndClear();
        Long tasteId = memberTasteRepository.findWithCategoriesByMemberId(mom.getId()).orElseThrow().getId();

        tasteService.deleteTaste(mom.getId());
        flushAndClear();

        assertThat(memberTasteRepository.findWithCategoriesByMemberId(mom.getId())).isEmpty();
        assertThat(countCategories(tasteId)).isZero();
    }

    @Test
    @DisplayName("회원 행이 삭제되면 취향과 선호 활동도 DB에서 함께 삭제된다.")
    void deleteWithMember() {
        tasteService.saveMyTaste(other.getId(), request(Category.EXHIBITION, Category.POPUP));
        flushAndClear();
        Long tasteId = memberTasteRepository.findWithCategoriesByMemberId(other.getId()).orElseThrow().getId();

        entityManager.createNativeQuery("delete from member where member_id = :memberId")
                .setParameter("memberId", other.getId())
                .executeUpdate();
        entityManager.clear();

        assertThat(memberTasteRepository.findById(tasteId)).isEmpty();
        assertThat(countCategories(tasteId)).isZero();
    }

    private TasteReqDTO.Save request(Category... categories) {
        return new TasteReqDTO.Save(List.of(categories), IndoorOutdoor.INDOOR, ActivityLevel.LIGHT,
                PriceLevel.FREE, DurationLevel.WITHIN_1H);
    }

    private long countCategories(Long tasteId) {
        Number count = (Number) entityManager
                .createNativeQuery("select count(*) from member_taste_category where member_taste_id = :tasteId")
                .setParameter("tasteId", tasteId)
                .getSingleResult();
        return count.longValue();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
