package com.kiri.server.domain.question.entity;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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

    // 답변 내용 수정
    public void updateContent(String content) {
        this.content = content;
    }
}
