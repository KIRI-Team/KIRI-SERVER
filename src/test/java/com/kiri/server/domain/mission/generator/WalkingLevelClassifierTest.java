package com.kiri.server.domain.mission.generator;

import com.kiri.server.domain.external.dto.NormalizedContent;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.WalkingLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class WalkingLevelClassifierTest {

    @ParameterizedTest
    @CsvSource({
            "PERFORMANCE, LOW",
            "FOOD_CAFE, LOW",
            "EXHIBITION, MEDIUM",
            "POPUP, MEDIUM",
            "EXPERIENCE, MEDIUM",
            "OUTDOOR, HIGH"
    })
    @DisplayName("단서가 없으면 카테고리 기본값으로 걷기량을 정한다.")
    void byCategoryDefault(Category category, WalkingLevel expected) {
        assertThat(WalkingLevelClassifier.classify(category, content(null, null))).isEqualTo(expected);
    }

    @Test
    @DisplayName("원본에 앉아서 진행한다는 단서가 있으면 적음으로 보정한다.")
    void sedentaryCueDowngradesToLow() {
        NormalizedContent experience = content("앉아서 진행하는 도자기 만들기 체험", null);
        NormalizedContent outdoor = content(null, "야외 좌석에서 즐기는 공연");

        assertThat(WalkingLevelClassifier.classify(Category.EXPERIENCE, experience)).isEqualTo(WalkingLevel.LOW);
        assertThat(WalkingLevelClassifier.classify(Category.OUTDOOR, outdoor)).isEqualTo(WalkingLevel.LOW);
    }

    @Test
    @DisplayName("단순 관람 표현만으로는 보정하지 않는다.")
    void plainViewingKeywordDoesNotDowngrade() {
        NormalizedContent exhibition = content("상설 전시를 관람할 수 있는 미술관", null);

        assertThat(WalkingLevelClassifier.classify(Category.EXHIBITION, exhibition)).isEqualTo(WalkingLevel.MEDIUM);
    }

    @Test
    @DisplayName("카테고리가 없으면 걷기량을 정하지 않는다.")
    void nullCategoryReturnsNull() {
        assertThat(WalkingLevelClassifier.classify(null, content(null, null))).isNull();
    }

    private NormalizedContent content(String overview, String program) {
        return NormalizedContent.builder()
                .externalId("1")
                .contentTypeId("12")
                .title("어느 장소")
                .overview(overview)
                .programTexts(program == null ? null : java.util.List.of(program))
                .build();
    }
}
