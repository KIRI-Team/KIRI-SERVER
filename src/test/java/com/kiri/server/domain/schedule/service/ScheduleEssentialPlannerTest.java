package com.kiri.server.domain.schedule.service;

import com.kiri.server.domain.mission.dto.MissionResDTO;
import com.kiri.server.domain.mission.entity.MissionChecklistItem;
import com.kiri.server.domain.mission.enums.ChecklistSource;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.mission.enums.ItemType;
import com.kiri.server.domain.mission.generator.EssentialPlanner;
import com.kiri.server.domain.weather.dto.WeatherSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ScheduleEssentialPlannerTest {

    private static final WeatherSummary MILD = new WeatherSummary(20, 15, 10, false);
    private static final WeatherSummary RAINY_COLD = new WeatherSummary(4, 1, 90, true);

    @Test
    @DisplayName("공식 준비물 > 날씨 > 활동 편의 > 혜택 물품 순으로 최대 4개를 반환한다.")
    void plan_ordersAndLimits() {
        List<MissionChecklistItem> items = List.of(
                item(ChecklistSource.KIRI, "편한 신발", "많이 걷는 일정이에요."),
                item(ChecklistSource.ORIGIN, "텀블러", EssentialPlanner.BENEFIT_DESCRIPTION),
                item(ChecklistSource.ORIGIN, "신분증", "공식 안내에 있는 준비물이에요."));

        List<MissionResDTO.ChecklistItem> result = ScheduleEssentialPlanner.plan(items, RAINY_COLD, IndoorOutdoor.OUTDOOR);

        assertThat(titles(result)).containsExactly("신분증", "우산", "겉옷·방한용품", "편한 신발");
    }

    @Test
    @DisplayName("운영자가 넣은 준비물은 공식 준비물로 맨 앞에 둔다.")
    void plan_operatorItemIsOfficial() {
        List<MissionChecklistItem> items = List.of(
                item(ChecklistSource.KIRI, "물", "야외에 오래 머무는 일정이에요."),
                item(ChecklistSource.OPERATOR, "운동화", "운영자가 안내한 준비물이에요."));

        assertThat(titles(ScheduleEssentialPlanner.plan(items, null, IndoorOutdoor.OUTDOOR)))
                .containsExactly("운동화", "물");
    }

    @Test
    @DisplayName("제목이 같으면 앞 순위 항목 하나만 남긴다.")
    void plan_dedupsByTitle() {
        List<MissionChecklistItem> items = List.of(item(ChecklistSource.ORIGIN, "우산", "공식 안내에 있는 준비물이에요."));

        assertThat(ScheduleEssentialPlanner.plan(items, RAINY_COLD, IndoorOutdoor.OUTDOOR))
                .extracting(MissionResDTO.ChecklistItem::title, MissionResDTO.ChecklistItem::description)
                .containsExactly(
                        tuple("우산", "공식 안내에 있는 준비물이에요."),
                        tuple("겉옷·방한용품", "최저 기온이 1도예요."));
    }

    @Test
    @DisplayName("실내 미션이면 날씨 항목을 붙이지 않는다.")
    void plan_indoorWithoutWeather() {
        List<MissionChecklistItem> items = List.of(item(ChecklistSource.KIRI, "편한 신발", "많이 걷는 일정이에요."));

        assertThat(titles(ScheduleEssentialPlanner.plan(items, RAINY_COLD, IndoorOutdoor.INDOOR)))
                .containsExactly("편한 신발");
    }

    @ParameterizedTest(name = "최고 {0} / 최저 {1} / 강수확률 {2} / 비·눈 {3} → {4}")
    @CsvSource(delimiter = '|', value = {
            "20 | 15 | 60 | false | 우산",
            "20 | 15 | 59 | false | ''",
            "20 | 15 | 0  | true  | 우산",
            "28 | 15 | 0  | false | 모자/양산",
            "27.9 | 15 | 0 | false | ''",
            "20 | 5  | 0  | false | 겉옷·방한용품",
            "20 | 5.1 | 0 | false | ''"
    })
    @DisplayName("날씨 항목은 강수확률 60%·비 예보, 최고 28도, 최저 5도를 기준으로 붙는다.")
    void plan_weatherThresholds(double max, double min, int probability, boolean precipitation, String expected) {
        WeatherSummary weather = new WeatherSummary(max, min, probability, precipitation);

        List<String> expectedTitles = expected.isEmpty() ? List.of() : Arrays.asList(expected.split(","));
        assertThat(titles(ScheduleEssentialPlanner.plan(List.of(), weather, IndoorOutdoor.OUTDOOR)))
                .containsExactlyElementsOf(expectedTitles);
    }

    @Test
    @DisplayName("날씨 항목 설명에 추천 이유를 담는다.")
    void plan_weatherDescriptions() {
        WeatherSummary weather = new WeatherSummary(31.4, 2.6, 70, false);

        assertThat(ScheduleEssentialPlanner.plan(List.of(), weather, IndoorOutdoor.BOTH))
                .extracting(MissionResDTO.ChecklistItem::description)
                .containsExactly("강수확률이 70%예요.", "최고 기온이 31도예요.", "최저 기온이 3도예요.");
    }

    @Test
    @DisplayName("날씨가 없으면 미션 준비물만 반환한다.")
    void plan_withoutWeather() {
        List<MissionChecklistItem> items = List.of(item(ChecklistSource.KIRI, "물", "야외에 오래 머무는 일정이에요."));

        assertThat(titles(ScheduleEssentialPlanner.plan(items, null, IndoorOutdoor.OUTDOOR))).containsExactly("물");
        assertThat(titles(ScheduleEssentialPlanner.plan(items, MILD, IndoorOutdoor.OUTDOOR))).containsExactly("물");
    }

    private static MissionChecklistItem item(ChecklistSource source, String title, String description) {
        return MissionChecklistItem.builder()
                .itemType(ItemType.PREPARE)
                .source(source)
                .title(title)
                .description(description)
                .build();
    }

    private static List<String> titles(List<MissionResDTO.ChecklistItem> items) {
        return items.stream().map(MissionResDTO.ChecklistItem::title).toList();
    }
}
