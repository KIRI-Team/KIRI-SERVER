package com.kiri.server.domain.mission.generator;

import com.kiri.server.domain.external.dto.NormalizedContent;
import com.kiri.server.domain.mission.enums.ChecklistSource;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.mission.enums.ItemType;
import com.kiri.server.domain.mission.enums.WalkingLevel;
import com.kiri.server.domain.mission.generator.dto.MissionDraft.ChecklistDraft;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    @DisplayName("'~지참' 앞에 쉼표로 나열된 물품을 각각 읽는다.")
    void officialItems_list() {
        assertThat(EssentialPlanner.officialItems("신분증, 입장권을 지참하세요.")).containsExactly("신분증", "입장권");
    }

    @Test
    @DisplayName("지참 금지·불필요 안내의 물품은 준비물로 보지 않는다.")
    void officialItems_skipsProhibited() {
        assertThat(EssentialPlanner.officialItems("카메라 지참 금지")).isEmpty();
        assertThat(EssentialPlanner.officialItems("돗자리는 지참하지 않아도 됩니다.")).isEmpty();
    }

    @Test
    @DisplayName("한 글자 물품은 담고, '꼭' 같은 기능어는 담지 않는다.")
    void officialItems_oneLetter() {
        assertThat(EssentialPlanner.officialItems("물을 지참하세요.")).containsExactly("물");
        assertThat(EssentialPlanner.officialItems("꼭 지참하세요.")).isEmpty();
    }

    @Test
    @DisplayName("할인·무료 같은 혜택 조건 문장의 지참 물품은 혜택 항목으로 따로 읽는다.")
    void benefitItems() {
        assertThat(EssentialPlanner.officialItems("신분증 지참 시 입장료 할인")).isEmpty();
        assertThat(EssentialPlanner.benefitItems("신분증 지참 시 입장료 할인")).containsExactly("신분증");
        assertThat(EssentialPlanner.benefitItems("입장 시 신분증을 지참해야 합니다.")).isEmpty();
        assertThat(EssentialPlanner.officialItems("편하게 오세요.")).isEmpty();
    }

    @Test
    @DisplayName("혜택 항목은 혜택 문구로 가장 뒤에 담는다.")
    void plan_benefitLast() {
        assertThat(EssentialPlanner.plan(content("공원", "넓은 잔디광장이 있어요.\n텀블러 지참 시 음료 할인", null),
                WalkingLevel.LOW, IndoorOutdoor.OUTDOOR))
                .extracting(ChecklistDraft::title, ChecklistDraft::description)
                .containsExactly(
                        tuple("돗자리", "잔디밭에서 쉬어 갈 수 있는 곳이에요."),
                        tuple("텀블러", "챙겨 가면 혜택을 받을 수 있어요."));
    }

    @Test
    @DisplayName("공식 준비물, 편한 신발, 물 순서로 담고 표시 순서를 1부터 매긴다.")
    void plan_priority() {
        NormalizedContent trail = content("북한산 둘레길", "준비물: 모자", "약 4시간");

        assertThat(EssentialPlanner.plan(trail, WalkingLevel.HIGH, IndoorOutdoor.OUTDOOR))
                .extracting(ChecklistDraft::itemType, ChecklistDraft::source, ChecklistDraft::title,
                        ChecklistDraft::displayOrder)
                .containsExactly(
                        tuple(ItemType.PREPARE, ChecklistSource.ORIGIN, "모자", 1),
                        tuple(ItemType.PREPARE, ChecklistSource.KIRI, "편한 신발", 2),
                        tuple(ItemType.PREPARE, ChecklistSource.KIRI, "물", 3));
    }

    @Test
    @DisplayName("걷기량이 높아도 원문에 오래 걷는 활동이 없으면 편한 신발을 담지 않는다.")
    void plan_shoes() {
        assertThat(EssentialPlanner.plan(content("순국선열추념탑", "독립운동을 기리는 탑", null),
                WalkingLevel.HIGH, IndoorOutdoor.OUTDOOR)).isEmpty();
        assertThat(EssentialPlanner.plan(content("한강 축제", "잠수교 걷기 행사", null),
                WalkingLevel.MEDIUM, null))
                .extracting(ChecklistDraft::title).containsExactly("편한 신발");
    }

    @Test
    @DisplayName("원본 소요시간이 3시간 이상인 야외 활동에만 물을 담고, 소요시간이 없으면 담지 않는다.")
    void plan_water() {
        assertThat(EssentialPlanner.plan(content("공원", null, "약 4시간"), WalkingLevel.LOW, IndoorOutdoor.INDOOR))
                .isEmpty();
        assertThat(EssentialPlanner.plan(content("공원", null, "약 2시간"), WalkingLevel.LOW, IndoorOutdoor.BOTH))
                .isEmpty();
        assertThat(EssentialPlanner.plan(content("공원", null, null), WalkingLevel.LOW, IndoorOutdoor.OUTDOOR))
                .isEmpty();
        assertThat(EssentialPlanner.plan(content("공원", null, "3시간"), WalkingLevel.LOW, IndoorOutdoor.OUTDOOR))
                .extracting(ChecklistDraft::title).containsExactly("물");
    }

    @Test
    @DisplayName("제목·소개·프로그램에 물놀이 활동이 있으면 여벌옷과 수건을 담는다.")
    void plan_waterPlay() {
        NormalizedContent kayak = NormalizedContent.builder()
                .title("한강 레저 체험").programTexts(List.of("카약 체험")).build();

        assertThat(EssentialPlanner.plan(kayak, WalkingLevel.MEDIUM, null))
                .extracting(ChecklistDraft::title).containsExactly("여벌옷", "수건");
        assertThat(EssentialPlanner.plan(content("음악분수 공연", null, null), WalkingLevel.LOW, null)).isEmpty();
    }

    @Test
    @DisplayName("잔디밭·피크닉 언급이 있는 야외 장소에만 돗자리를 담는다.")
    void plan_picnic() {
        assertThat(EssentialPlanner.plan(content("공원", "넓은 잔디광장이 있어요.", null), WalkingLevel.LOW,
                IndoorOutdoor.OUTDOOR))
                .extracting(ChecklistDraft::title).containsExactly("돗자리");
        assertThat(EssentialPlanner.plan(content("미술관", "잔디광장 옆 전시관", null), WalkingLevel.LOW,
                IndoorOutdoor.INDOOR)).isEmpty();
    }

    @Test
    @DisplayName("최대 4개까지만 담고, 공식 준비물과 이름이 같은 항목은 다시 담지 않는다.")
    void plan_limitAndDedup() {
        assertThat(EssentialPlanner.plan(content("둘레길", "준비물 : 편한 신발, 수건, 여벌옷, 모자, 선크림", "약 4시간"),
                WalkingLevel.HIGH, IndoorOutdoor.OUTDOOR))
                .extracting(ChecklistDraft::title)
                .containsExactly("편한 신발", "수건", "여벌옷", "모자");
    }

    private NormalizedContent content(String title, String overview, String durationText) {
        return NormalizedContent.builder().title(title).overview(overview).durationText(durationText).build();
    }
}
