package com.kiri.server.domain.taste.dto;

import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.taste.enums.ActivityLevel;
import com.kiri.server.domain.taste.enums.DurationLevel;
import com.kiri.server.domain.taste.enums.PriceLevel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class TasteReqDTO {

    public record Save(
            @NotNull(message = "선호 활동은 필수입니다.")
            @Size(min = 1, max = 3, message = "선호 활동은 1~3개 선택해야 합니다.")
            List<@NotNull(message = "선호 활동은 null일 수 없습니다.") Category> categories,

            IndoorOutdoor indoorOutdoor,

            @NotNull(message = "활동량은 필수입니다.")
            ActivityLevel activityLevel,

            PriceLevel priceLevel,

            DurationLevel durationLevel
    ) {}
}
