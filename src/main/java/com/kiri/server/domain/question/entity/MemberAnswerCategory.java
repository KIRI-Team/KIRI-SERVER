package com.kiri.server.domain.question.entity;

import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(
        name = "member_answer_category",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_member_answer_category", columnNames = {"member_answer_id", "category"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberAnswerCategory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_answer_category_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_answer_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MemberAnswer memberAnswer;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20, nullable = false)
    private Category category;
}
