package com.kiri.server.domain.question.repository;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import com.kiri.server.domain.question.entity.Question;
import com.kiri.server.domain.question.enums.TasteType;
import com.kiri.server.domain.question.repository.FamilyQuestionRepository.TasteTypeCount;
import com.kiri.server.global.config.JpaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class FamilyQuestionRepositoryTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 10, 1);

    @Autowired
    private FamilyQuestionRepository familyQuestionRepository;

    @Autowired
    private TestEntityManager em;

    private Family family;
    private Family otherFamily;

    @BeforeEach
    void setUp() {
        family = persistFamily("집계테스트가족", "집계테스트아빠");
        otherFamily = persistFamily("집계테스트이웃", "집계테스트이웃아빠");
    }

    @Test
    @DisplayName("취향 종류별 배정 횟수를 일반 질문(null) 포함해 별칭에 맞게 집계하고 다른 가족의 배정은 세지 않는다.")
    void countAssignedByTasteType() {
        Question general = persistQuestion("일반 질문", null);
        Question category1 = persistQuestion("카테고리 질문1", TasteType.CATEGORY);
        Question category2 = persistQuestion("카테고리 질문2", TasteType.CATEGORY);
        Question price = persistQuestion("가격 질문", TasteType.PRICE);
        persistFamilyQuestion(family, general, BASE_DATE);
        persistFamilyQuestion(family, general, BASE_DATE.plusDays(1));
        persistFamilyQuestion(family, category1, BASE_DATE.plusDays(2));
        persistFamilyQuestion(family, category2, BASE_DATE.plusDays(3));
        persistFamilyQuestion(otherFamily, price, BASE_DATE);
        em.flush();
        em.clear();

        Map<TasteType, Long> counts = new HashMap<>();
        for (TasteTypeCount count : familyQuestionRepository.countAssignedByTasteType(family)) {
            counts.put(count.getTasteType(), count.getAssignedCount());
        }

        assertThat(counts).hasSize(2)
                .containsEntry(null, 2L)
                .containsEntry(TasteType.CATEGORY, 2L)
                .doesNotContainKey(TasteType.PRICE);
    }

    @Test
    @DisplayName("가족의 해당 날짜에 배정된 질문이 있는지 확인한다.")
    void existsByFamilyAndQuestionDate() {
        persistFamilyQuestion(family, persistQuestion("일반 질문", null), BASE_DATE);
        em.flush();
        em.clear();

        assertThat(familyQuestionRepository.existsByFamilyAndQuestionDate(family, BASE_DATE)).isTrue();
        assertThat(familyQuestionRepository.existsByFamilyAndQuestionDate(family, BASE_DATE.plusDays(1))).isFalse();
        assertThat(familyQuestionRepository.existsByFamilyAndQuestionDate(otherFamily, BASE_DATE)).isFalse();
    }

    private Family persistFamily(String name, String ownerNickname) {
        Member owner = em.persist(Member.builder().nickname(ownerNickname).build());
        return em.persist(Family.builder().name(name).owner(owner).build());
    }

    private Question persistQuestion(String content, TasteType tasteType) {
        return em.persist(Question.builder().content(content).tasteType(tasteType).build());
    }

    private void persistFamilyQuestion(Family targetFamily, Question question, LocalDate date) {
        em.persist(FamilyQuestion.builder().family(targetFamily).question(question).questionDate(date).build());
    }
}
