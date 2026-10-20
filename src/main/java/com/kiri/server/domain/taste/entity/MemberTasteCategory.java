package com.kiri.server.domain.taste.entity;

import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "member_taste_category",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_member_taste_category", columnNames = {"member_taste_id", "category"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberTasteCategory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_taste_category_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_taste_id", nullable = false)
    private MemberTaste memberTaste;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20, nullable = false)
    private Category category;
}
