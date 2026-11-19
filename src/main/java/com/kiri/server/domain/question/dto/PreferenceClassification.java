package com.kiri.server.domain.question.dto;

import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.taste.enums.ActivityLevel;
import com.kiri.server.domain.taste.enums.DurationLevel;
import com.kiri.server.domain.taste.enums.PriceLevel;

import java.util.List;

public record PreferenceClassification(
        List<Category> categories,
        IndoorOutdoor indoorOutdoor,
        DurationLevel durationLevel,
        PriceLevel priceLevel,
        ActivityLevel activityLevel
) {}
