package com.kiri.server.domain.taste.dto;

import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.taste.enums.ActivityLevel;
import com.kiri.server.domain.taste.enums.DurationLevel;
import com.kiri.server.domain.taste.enums.PriceLevel;
import lombok.Builder;

import java.util.List;

public class TasteResDTO {

    @Builder
    public record MyTaste(
            String nickname,
            Boolean isSet,
            List<Category> categories,
            IndoorOutdoor indoorOutdoor,
            ActivityLevel activityLevel,
            PriceLevel priceLevel,
            DurationLevel durationLevel
    ) {}
}
