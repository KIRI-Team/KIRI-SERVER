package com.kiri.server.domain.taste.entity;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.taste.enums.ActivityLevel;
import com.kiri.server.domain.taste.enums.DurationLevel;
import com.kiri.server.domain.taste.enums.PriceLevel;
import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(
        name = "member_taste",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_member_taste_member", columnNames = {"member_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberTaste extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_taste_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "indoor_outdoor", length = 20)
    private IndoorOutdoor indoorOutdoor;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level", length = 20, nullable = false)
    private ActivityLevel activityLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_level", length = 20)
    private PriceLevel priceLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "duration_level", length = 20)
    private DurationLevel durationLevel;

    @Builder.Default
    @OneToMany(mappedBy = "memberTaste", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MemberTasteCategory> categories = new ArrayList<>();

    public void update(IndoorOutdoor indoorOutdoor, ActivityLevel activityLevel, PriceLevel priceLevel,
                       DurationLevel durationLevel, Collection<Category> selected) {
        this.indoorOutdoor = indoorOutdoor;
        this.activityLevel = activityLevel;
        this.priceLevel = priceLevel;
        this.durationLevel = durationLevel;

        // 남는 카테고리 행은 그대로 둬야 (member_taste_id, category) 유니크 제약에 걸리지 않음
        categories.removeIf(item -> !selected.contains(item.getCategory()));
        Set<Category> kept = categories.stream()
                .map(MemberTasteCategory::getCategory)
                .collect(Collectors.toSet());
        selected.stream()
                .distinct()
                .filter(category -> !kept.contains(category))
                .forEach(category -> categories.add(MemberTasteCategory.builder()
                        .memberTaste(this)
                        .category(category)
                        .build()));
    }

    public List<Category> getCategoryValues() {
        return categories.stream()
                .map(MemberTasteCategory::getCategory)
                .sorted()
                .toList();
    }
}
