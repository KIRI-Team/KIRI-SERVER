package com.kiri.server.domain.schedule.service;

import com.kiri.server.domain.mission.dto.MissionResDTO;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.weather.dto.WeatherSummary;

import java.util.ArrayList;
import java.util.List;

// 일정 구간 날씨로 붙일 맞춤템 (우산 > 모자/양산 > 겉옷·방한용품)
public final class ScheduleEssentialPlanner {

    static final int RAIN_PROBABILITY = 60;
    static final double HOT_TEMPERATURE = 28;
    static final double COLD_TEMPERATURE = 5;

    private ScheduleEssentialPlanner() {
    }

    public static boolean isOutdoor(IndoorOutdoor indoorOutdoor) {
        return indoorOutdoor == IndoorOutdoor.OUTDOOR || indoorOutdoor == IndoorOutdoor.BOTH;
    }

    public static List<MissionResDTO.ChecklistItem> weatherItems(WeatherSummary weather) {
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
