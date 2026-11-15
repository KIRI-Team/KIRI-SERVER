package com.kiri.server.domain.schedule.service;

import com.kiri.server.domain.mission.dto.MissionResDTO;
import com.kiri.server.domain.weather.dto.WeatherSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleEssentialPlannerTest {

    @ParameterizedTest(name = "최고 {0} / 최저 {1} / 강수확률 {2} / 비·눈 {3} → {4}")
    @CsvSource(delimiter = '|', value = {
            "20   | 15  | 60 | false | 우산",
            "20   | 15  | 59 | false | ''",
            "20   | 15  | 0  | true  | 우산",
            "28   | 15  | 0  | false | 모자/양산",
            "27.9 | 15  | 0  | false | ''",
            "20   | 5   | 0  | false | 겉옷·방한용품",
            "20   | 5.1 | 0  | false | ''"
    })
    @DisplayName("강수확률 60%·비 예보, 최고 28도, 최저 5도를 기준으로 날씨 항목을 만든다.")
    void weatherItems_thresholds(double max, double min, int probability, boolean precipitation, String expected) {
        WeatherSummary weather = new WeatherSummary(max, min, probability, precipitation);

        List<String> expectedTitles = expected.isEmpty() ? List.of() : Arrays.asList(expected.split(","));
        assertThat(ScheduleEssentialPlanner.weatherItems(weather))
                .extracting(MissionResDTO.ChecklistItem::title)
                .containsExactlyElementsOf(expectedTitles);
    }

    @Test
    @DisplayName("우산 > 모자/양산 > 겉옷·방한용품 순서로, 설명에 추천 이유를 담는다.")
    void weatherItems_descriptions() {
        WeatherSummary weather = new WeatherSummary(31.4, 2.6, 70, false);

        assertThat(ScheduleEssentialPlanner.weatherItems(weather))
                .extracting(MissionResDTO.ChecklistItem::description)
                .containsExactly("강수확률이 70%예요.", "최고 기온이 31도예요.", "최저 기온이 3도예요.");
    }

    @Test
    @DisplayName("비·눈 예보가 있으면 강수확률 대신 예보 문구를 쓴다.")
    void weatherItems_precipitationDescription() {
        assertThat(ScheduleEssentialPlanner.weatherItems(new WeatherSummary(15, 10, 90, true)))
                .extracting(MissionResDTO.ChecklistItem::description)
                .containsExactly("비나 눈 예보가 있어요.");
    }
}
