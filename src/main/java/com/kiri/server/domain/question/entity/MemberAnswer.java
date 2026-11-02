package com.kiri.server.domain.question.entity;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.taste.enums.ActivityLevel;
import com.kiri.server.domain.taste.enums.DurationLevel;
import com.kiri.server.domain.taste.enums.PriceLevel;
import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "member_answer",
        uniqueConstraints = {
                // 한 회원은 가족 질문 하나에 답변 하나만 작성
                @UniqueConstraint(name = "uk_member_answer", columnNames = {"member_id", "family_question_id"})
        }
)
public class MemberAnswer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_answer_id")
    private Long id;

    // 답변 내용 (답변을 작성할 때 행이 생기므로 행이 있으면 답변한 것)
    @Column(name = "content", length = 500, nullable = false)
    private String content;

    // 가족 질문
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_question_id", nullable = false)
    private FamilyQuestion familyQuestion;

    // 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "indoor_outdoor", length = 20)
    private IndoorOutdoor indoorOutdoor;

    @Enumerated(EnumType.STRING)
    @Column(name = "duration_level", length = 20)
    private DurationLevel durationLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_level", length = 20)
    private PriceLevel priceLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level", length = 20)
    private ActivityLevel activityLevel;

    // 답변 내용 수정
    public void updateContent(String content) {
        this.content = content;
    }

    public void updateClassification(
            IndoorOutdoor indoorOutdoor,
            DurationLevel durationLevel,
            PriceLevel priceLevel,
            ActivityLevel activityLevel
    ) {
        this.indoorOutdoor = indoorOutdoor;
        this.durationLevel = durationLevel;
        this.priceLevel = priceLevel;
        this.activityLevel = activityLevel;
    }
}
