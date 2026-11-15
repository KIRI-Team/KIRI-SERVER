package com.kiri.server.domain.schedule.service;

import com.kiri.server.domain.mission.dto.MissionResDTO;
import com.kiri.server.domain.mission.entity.MissionChecklistItem;
import com.kiri.server.domain.mission.enums.ChecklistSource;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.mission.generator.EssentialPlanner;
import com.kiri.server.domain.weather.dto.WeatherSummary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 공식 준비물 > 날씨 대응 > 활동 편의 > 혜택 물품 순으로 최대 4개
public final class ScheduleEssentialPlanner {

    static final int RAIN_PROBABILITY = 60;
    static final double HOT_TEMPERATURE = 28;
    static final double COLD_TEMPERATURE = 5;

    private ScheduleEssentialPlanner() {
    }

    public static boolean isOutdoor(IndoorOutdoor indoorOutdoor) {
        return indoorOutdoor == IndoorOutdoor.OUTDOOR || indoorOutdoor == IndoorOutdoor.BOTH;
    }

    // weather가 null이면 날씨 항목 없이 미션 근거 항목만
    public static List<MissionResDTO.ChecklistItem> plan(List<MissionChecklistItem> missionItems,
                                                         WeatherSummary weather, IndoorOutdoor indoorOutdoor) {
        List<MissionResDTO.ChecklistItem> official = new ArrayList<>();
        List<MissionResDTO.ChecklistItem> convenience = new ArrayList<>();
        List<MissionResDTO.ChecklistItem> benefit = new ArrayList<>();
        for (MissionChecklistItem item : missionItems) {
            MissionResDTO.ChecklistItem essential = toItem(item.getTitle(), item.getDescription());
            if (item.getSource() == ChecklistSource.KIRI) {
                convenience.add(essential);
            } else if (EssentialPlanner.BENEFIT_DESCRIPTION.equals(item.getDescription())) {
                benefit.add(essential);
            } else {
                official.add(essential);
            }
        }
        List<MissionResDTO.ChecklistItem> weatherItems = weather == null || !isOutdoor(indoorOutdoor)
                ? List.of() : weatherItems(weather);

        Map<String, MissionResDTO.ChecklistItem> essentials = new LinkedHashMap<>();
        for (List<MissionResDTO.ChecklistItem> group : List.of(official, weatherItems, convenience, benefit)) {
            group.forEach(item -> essentials.putIfAbsent(item.title(), item));
        }
        return essentials.values().stream().limit(EssentialPlanner.MAX_ESSENTIALS).toList();
    }

    private static List<MissionResDTO.ChecklistItem> weatherItems(WeatherSummary weather) {
        List<MissionResDTO.ChecklistItem> items = new ArrayList<>();
        if (weather.precipitation()) {
            items.add(toItem("우산", "비나 눈 예보가 있어요."));
        } else if (weather.maxPrecipitationProbability() >= RAIN_PROBABILITY) {
            items.add(toItem("우산", "강수확률이 " + weather.maxPrecipitationProbability() + "%예요."));
        }
        if (weather.maxTemperature() >= HOT_TEMPERATURE) {
            items.add(toItem("모자/양산", "최고 기온이 " + Math.round(weather.maxTemperature()) + "도예요."));
        }
        if (weather.minTemperature() <= COLD_TEMPERATURE) {
            items.add(toItem("겉옷·방한용품", "최저 기온이 " + Math.round(weather.minTemperature()) + "도예요."));
        }
        return items;
    }

    private static MissionResDTO.ChecklistItem toItem(String title, String description) {
        return MissionResDTO.ChecklistItem.builder()
                .title(title)
                .description(description)
                .build();
    }
}
