package com.kiri.server.domain.question.entity;

import com.kiri.server.domain.question.enums.TasteType;
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
        name = "question",
        uniqueConstraints = {
                // N일차 고정 질문은 하나만 (일반 질문은 fixed_day가 null이라 제약 대상 아님)
                @UniqueConstraint(name = "uk_question_fixed_day", columnNames = {"fixed_day"})
        }
)
public class Question extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id")
    private Long id;

    // 질문 내용
    @Column(name = "content", nullable = false)
    private String content;

    // 활성화 여부(가족에게 질문 배정시 활성화 상태에서만 배정, soft delete 고려)
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // 고정 노출 일차 (1~7: 가족 생성 후 N일차에 고정 배정, null: 일반 랜덤 질문)
    @Column(name = "fixed_day")
    private Integer fixedDay;

    // 수집 취향 종류
    @Enumerated(EnumType.STRING)
    @Column(name = "taste_type", length = 20)
    private TasteType tasteType;
}
