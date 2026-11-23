package com.kiri.server.domain.question;

import com.kiri.server.admin.AdminFixtures;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.question.converter.QuestionConverter;
import com.kiri.server.domain.question.dto.PreferenceClassification;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import com.kiri.server.domain.question.entity.MemberAnswer;
import com.kiri.server.domain.question.entity.Question;
import com.kiri.server.domain.question.enums.TasteType;
import com.kiri.server.domain.question.service.AnswerClassificationService;
import com.kiri.server.domain.taste.enums.PriceLevel;
import com.kiri.server.global.ai.service.AiService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Transactional
@Import(AdminFixtures.class)
class AnswerClassificationIntegrationTest {

    @Autowired
    private AdminFixtures fx;
    @Autowired
    private EntityManager em;
    @Autowired
    private AnswerClassificationService answerClassificationService;
    @MockitoBean
    private AiService aiService;

    private Member mom;
    private Family family;

    @BeforeEach
    void setUp() {
        mom = fx.member("엄마");
        family = fx.family("우리집", mom);
    }

    private MemberAnswer answer(TasteType tasteType, String questionText, String content) {
        Question question = Question.builder().content(questionText).tasteType(tasteType).build();
        em.persist(question);
        FamilyQuestion familyQuestion = FamilyQuestion.builder()
                .family(family).question(question).questionDate(LocalDate.of(2026, 11, 24)).build();
        em.persist(familyQuestion);
        MemberAnswer answer = QuestionConverter.toMemberAnswer(content, familyQuestion, mom);
        em.persist(answer);
        em.flush();
        return answer;
    }

    private List<Category> categoriesOf(MemberAnswer answer) {
        return em.createQuery("select c.category from MemberAnswerCategory c where c.memberAnswer.id = :id order by c.id", Category.class)
                .setParameter("id", answer.getId())
                .getResultList();
    }

    @Test
    @DisplayName("카테고리 질문 답변은 분류된 카테고리를 저장하고, 다시 분류하면 이전 카테고리를 갈아끼운다")
    void categoryAnswer() {
        MemberAnswer answer = answer(TasteType.CATEGORY, "가족과 어떤 활동을 하고 싶나요?", "전시회나 맛집에 가고 싶어요.");
        given(aiService.classify(TasteType.CATEGORY, "가족과 어떤 활동을 하고 싶나요?", "전시회나 맛집에 가고 싶어요."))
                .willReturn(new PreferenceClassification(List.of(Category.EXHIBITION, Category.FOOD_CAFE), null, null, null, null));

        answerClassificationService.classify(answer.getId());
        em.flush();
        assertThat(categoriesOf(answer)).containsExactly(Category.EXHIBITION, Category.FOOD_CAFE);

        given(aiService.classify(TasteType.CATEGORY, "가족과 어떤 활동을 하고 싶나요?", "전시회나 맛집에 가고 싶어요."))
                .willReturn(new PreferenceClassification(List.of(Category.OUTDOOR), null, null, null, null));

        answerClassificationService.classify(answer.getId());
        em.flush();
        assertThat(categoriesOf(answer)).containsExactly(Category.OUTDOOR);
    }

    @Test
    @DisplayName("비용 질문 답변은 비용 수준을 답변에 저장하고 카테고리는 만들지 않는다")
    void priceAnswer() {
        MemberAnswer answer = answer(TasteType.PRICE, "비용은 어느 정도가 적당한가요?", "돈이 많이 들지 않았으면 좋겠어요.");
        given(aiService.classify(any(), any(), any()))
                .willReturn(new PreferenceClassification(List.of(), null, null, PriceLevel.LOW, null));

        answerClassificationService.classify(answer.getId());
        em.flush();
        em.clear();

        assertThat(em.find(MemberAnswer.class, answer.getId()).getPriceLevel()).isEqualTo(PriceLevel.LOW);
        assertThat(categoriesOf(answer)).isEmpty();
    }

    @Test
    @DisplayName("취향 질문이 아니면 AI를 부르지 않는다")
    void generalQuestionIsSkipped() {
        MemberAnswer answer = answer(null, "오늘 가장 웃겼던 일은?", "강아지가 넘어졌어요.");

        answerClassificationService.classify(answer.getId());

        verify(aiService, never()).classify(any(), any(), any());
    }

    @Test
    @DisplayName("없는 답변이면 아무것도 하지 않는다")
    void missingAnswerIsSkipped() {
        answerClassificationService.classify(-1L);

        verify(aiService, never()).classify(any(), any(), any());
    }

    @Test
    @DisplayName("AI를 기다리는 사이 답변이 수정되면 옛 내용의 분류 결과로 덮지 않는다")
    void staleResultIsDiscarded() {
        MemberAnswer answer = answer(TasteType.PRICE, "비용은 어느 정도가 적당한가요?", "무료가 좋아요.");
        given(aiService.classify(any(), any(), any())).willAnswer(invocation -> {
            answer.updateContent("비싸도 괜찮아요.");
            return new PreferenceClassification(List.of(), null, null, PriceLevel.FREE, null);
        });

        answerClassificationService.classify(answer.getId());

        assertThat(answer.getPriceLevel()).isNull();
    }
}
