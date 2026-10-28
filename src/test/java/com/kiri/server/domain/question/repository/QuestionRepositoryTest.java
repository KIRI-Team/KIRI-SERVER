package com.kiri.server.domain.question.repository;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import com.kiri.server.domain.question.entity.Question;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 로컬 DB에 시드 데이터가 있을 수 있어 전체 결과가 아닌 이 테스트에서 만든 질문 포함 여부로 검증
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class QuestionRepositoryTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 10, 1);

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private TestEntityManager em;

    private Family family;
    private Family otherFamily;

    @BeforeEach
    void setUp() {
        family = persistFamily("배정테스트가족", "배정테스트아빠");
        otherFamily = persistFamily("배정테스트이웃", "배정테스트이웃아빠");
    }

    @Test
    @DisplayName("미사용 질문 풀은 가족이 받은 질문, 고정 질문, 비활성 질문을 제외하고 다른 가족이 받은 질문은 포함한다.")
    void findUnassignedPoolQuestions() {
        Question unused = persistQuestion("아직 안 받은 질문", null, true);
        Question received = persistQuestion("이미 받은 질문", null, true);
        Question receivedByOther = persistQuestion("다른 가족만 받은 질문", null, true);
        // uk_question_fixed_day 때문에 시드의 1~7일차와 겹치지 않는 값 사용
        Question fixed = persistQuestion("고정 질문", 101, true);
        Question inactive = persistQuestion("비활성 질문", null, false);
        persistFamilyQuestion(family, received, BASE_DATE);
        persistFamilyQuestion(otherFamily, receivedByOther, BASE_DATE);
        em.flush();
        em.clear();

        List<Long> ids = questionRepository.findUnassignedPoolQuestions(family).stream()
                .map(Question::getId)
                .toList();

        assertThat(ids).contains(unused.getId(), receivedByOther.getId());
        assertThat(ids).doesNotContain(received.getId(), fixed.getId(), inactive.getId());
    }

    @Test
    @DisplayName("받은 질문 풀은 마지막 배정일이 오래된 순으로 정렬하고 고정 질문과 비활성 질문은 제외한다.")
    void findAssignedPoolQuestionsOrderByLastAssigned() {
        Question oldest = persistQuestion("가장 오래전에 받은 질문", null, true);
        Question reassigned = persistQuestion("예전에 받고 최근에 또 받은 질문", null, true);
        Question recent = persistQuestion("최근에 받은 질문", null, true);
        Question fixed = persistQuestion("고정 질문", 102, true);
        Question inactive = persistQuestion("비활성 질문", null, false);
        persistFamilyQuestion(family, reassigned, BASE_DATE);
        persistFamilyQuestion(family, oldest, BASE_DATE.plusDays(1));
        persistFamilyQuestion(family, recent, BASE_DATE.plusDays(2));
        persistFamilyQuestion(family, reassigned, BASE_DATE.plusDays(3));
        persistFamilyQuestion(family, fixed, BASE_DATE.minusDays(1));
        persistFamilyQuestion(family, inactive, BASE_DATE.minusDays(2));
        em.flush();
        em.clear();

        List<Long> ids = questionRepository.findAssignedPoolQuestionsOrderByLastAssigned(family).stream()
                .map(Question::getId)
                .toList();

        assertThat(ids).containsExactly(oldest.getId(), recent.getId(), reassigned.getId());
    }

    private Family persistFamily(String name, String ownerNickname) {
        Member owner = em.persist(Member.builder().nickname(ownerNickname).build());
        return em.persist(Family.builder().name(name).owner(owner).build());
    }

    private Question persistQuestion(String content, Integer fixedDay, boolean isActive) {
        return em.persist(Question.builder().content(content).fixedDay(fixedDay).isActive(isActive).build());
    }

    private void persistFamilyQuestion(Family targetFamily, Question question, LocalDate date) {
        em.persist(FamilyQuestion.builder().family(targetFamily).question(question).questionDate(date).build());
    }
}
