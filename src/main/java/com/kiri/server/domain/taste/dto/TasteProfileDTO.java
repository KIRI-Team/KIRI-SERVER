package com.kiri.server.domain.taste.dto;

import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.taste.enums.ActivityLevel;
import com.kiri.server.domain.taste.enums.DurationLevel;
import com.kiri.server.domain.taste.enums.PriceLevel;
import lombok.Builder;

import java.util.List;

public class TasteProfileDTO {

    // null 값은 상관없음, durationMaxMinutes가 null이면 상한 없음
    @Builder
    public record MemberProfile(
            Long memberId,
            boolean isSet,
            List<Category> categories,
            IndoorOutdoor indoorOutdoor,
            ActivityLevel activityLevel,
            PriceLevel priceLevel,
            DurationLevel durationLevel,
            Integer durationMinMinutes,
            Integer durationMaxMinutes
    ) {}

    @Builder
    public record FamilyProfile(
            Long familyId,
            List<MemberProfile> members,
            int setMemberCount
    ) {}
}
