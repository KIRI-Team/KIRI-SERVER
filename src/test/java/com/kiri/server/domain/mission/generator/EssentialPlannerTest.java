package com.kiri.server.domain.mission.generator;

import com.kiri.server.domain.mission.enums.ChecklistSource;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.mission.enums.ItemType;
import com.kiri.server.domain.mission.enums.WalkingLevel;
import com.kiri.server.domain.mission.generator.dto.MissionDraft.ChecklistDraft;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class EssentialPlannerTest {

    @Test
    @DisplayName("준비물 라벨 뒤 항목과 '~지참' 앞 명사를 공식 준비물로 읽는다.")
    void officialItems() {
        assertThat(EssentialPlanner.officialItems("준비물 : 수건, 여벌옷")).containsExactly("수건", "여벌옷");
        assertThat(EssentialPlanner.officialItems("행사장에는 개인 텀블러 등을 꼭 지참하세요."))
                .containsExactly("개인 텀블러");
        assertThat(EssentialPlanner.officialItems("입장 시 신분증을 지참해야 합니다.")).containsExactly("신분증");
    }

    @Test
    @DisplayName("할인·무료 같은 혜택 조건 문장의 지참 물품은 준비물로 보지 않는다.")
    void officialItems_skipsBenefit() {
        assertThat(EssentialPlanner.officialItems("신분증 지참 시 입장료 할인")).isEmpty();
        assertThat(EssentialPlanner.officialItems("편하게 오세요.")).isEmpty();
    }

    @Test
    @DisplayName("공식 준비물, 편한 신발, 물 순서로 담고 표시 순서를 1부터 매긴다.")
    void plan_priority() {
        assertThat(EssentialPlanner.plan("준비물: 수건", WalkingLevel.HIGH, IndoorOutdoor.OUTDOOR, 240))
                .extracting(ChecklistDraft::itemType, ChecklistDraft::source, ChecklistDraft::title,
                        ChecklistDraft::displayOrder)
                .containsExactly(
                        tuple(ItemType.PREPARE, ChecklistSource.ORIGIN, "수건", 1),
                        tuple(ItemType.PREPARE, ChecklistSource.KIRI, "편한 신발", 2),
                        tuple(ItemType.PREPARE, ChecklistSource.KIRI, "물", 3));
    }

    @Test
    @DisplayName("실내이거나 예상 최대 활동시간이 3시간 미만이면 물을 담지 않는다.")
    void plan_water() {
        assertThat(EssentialPlanner.plan(null, WalkingLevel.LOW, IndoorOutdoor.INDOOR, 240)).isEmpty();
        assertThat(EssentialPlanner.plan(null, WalkingLevel.LOW, IndoorOutdoor.BOTH, 179)).isEmpty();
        assertThat(EssentialPlanner.plan(null, WalkingLevel.LOW, IndoorOutdoor.OUTDOOR, 180))
                .extracting(ChecklistDraft::title).containsExactly("물");
    }

    @Test
    @DisplayName("최대 4개까지만 담고, 공식 준비물과 이름이 같은 항목은 다시 담지 않는다.")
    void plan_limitAndDedup() {
        assertThat(EssentialPlanner.plan("준비물 : 편한 신발, 수건, 여벌옷, 모자, 선크림",
                WalkingLevel.HIGH, IndoorOutdoor.OUTDOOR, 240))
                .extracting(ChecklistDraft::title)
                .containsExactly("편한 신발", "수건", "여벌옷", "모자");
    }
}
