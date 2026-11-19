package com.kiri.server.domain.mission.generator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChecklistPlannerTest {

    @Test
    @DisplayName("행사 프로그램 이름에는 항상 동사를 붙이고, 이미 동사로 끝나면 그대로 둔다.")
    void toActivity() {
        assertThat(ChecklistPlanner.toActivity("잠수교 걷기")).isEqualTo("잠수교 걷기");
        assertThat(ChecklistPlanner.toActivity("선유도 새산책")).isEqualTo("선유도 새산책하기");
        assertThat(ChecklistPlanner.toActivity("나비와 자연생태 관찰 및 체험")).isEqualTo("나비와 자연생태 관찰 및 체험하기");
        assertThat(ChecklistPlanner.toActivity("버스킹 공연")).isEqualTo("버스킹 공연 관람하기");
        assertThat(ChecklistPlanner.toActivity("초청공연(원슈타인, 이영지)")).isEqualTo("초청공연(원슈타인, 이영지) 관람하기");
        assertThat(ChecklistPlanner.toActivity("러닝 클래스")).isEqualTo("러닝 클래스 참여하기");
        assertThat(ChecklistPlanner.toActivity("폐막식")).isEqualTo("폐막식 참여하기");
        assertThat(ChecklistPlanner.toActivity("미디어파사드")).isEqualTo("미디어파사드 함께 즐기기");
    }
}
