package com.kiri.server.domain.taste.entity;

import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.taste.enums.ActivityLevel;
import com.kiri.server.domain.taste.enums.DurationLevel;
import com.kiri.server.domain.taste.enums.PriceLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemberTasteTest {

    @Test
    @DisplayName("취향 값을 덮어쓰고 선호 활동은 화면 순서대로 돌려준다.")
    void update() {
        MemberTaste taste = MemberTaste.builder().build();

        taste.update(IndoorOutdoor.INDOOR, ActivityLevel.LIGHT, PriceLevel.FREE, DurationLevel.WITHIN_1H,
                List.of(Category.FOOD_CAFE, Category.EXHIBITION));

        assertThat(taste.getIndoorOutdoor()).isEqualTo(IndoorOutdoor.INDOOR);
        assertThat(taste.getActivityLevel()).isEqualTo(ActivityLevel.LIGHT);
        assertThat(taste.getPriceLevel()).isEqualTo(PriceLevel.FREE);
        assertThat(taste.getDurationLevel()).isEqualTo(DurationLevel.WITHIN_1H);
        assertThat(taste.getCategoryValues()).containsExactly(Category.EXHIBITION, Category.FOOD_CAFE);
        assertThat(taste.getCategories()).allMatch(item -> item.getMemberTaste() == taste);
    }

    @Test
    @DisplayName("다시 저장하면 남는 선호 활동 행은 그대로 두고 빠진 것만 지우고 새로 고른 것만 추가한다.")
    void updateCategories() {
        MemberTaste taste = MemberTaste.builder().build();
        taste.update(null, ActivityLevel.MODERATE, null, null, List.of(Category.EXHIBITION, Category.FOOD_CAFE));
        MemberTasteCategory kept = taste.getCategories().stream()
                .filter(item -> item.getCategory() == Category.FOOD_CAFE)
                .findFirst()
                .orElseThrow();

        taste.update(null, ActivityLevel.MODERATE, null, null, List.of(Category.FOOD_CAFE, Category.POPUP));

        assertThat(taste.getCategoryValues()).containsExactly(Category.POPUP, Category.FOOD_CAFE);
        assertThat(taste.getCategories()).hasSize(2).contains(kept);
    }

    @Test
    @DisplayName("상관없음(null)으로 바꾸면 기존 값을 지운다.")
    void updateToAny() {
        MemberTaste taste = MemberTaste.builder().build();
        taste.update(IndoorOutdoor.OUTDOOR, ActivityLevel.ACTIVE, PriceLevel.LOW, DurationLevel.HALF_DAY_PLUS,
                List.of(Category.OUTDOOR));

        taste.update(null, ActivityLevel.ACTIVE, null, null, List.of(Category.OUTDOOR));

        assertThat(taste.getIndoorOutdoor()).isNull();
        assertThat(taste.getPriceLevel()).isNull();
        assertThat(taste.getDurationLevel()).isNull();
    }
}
