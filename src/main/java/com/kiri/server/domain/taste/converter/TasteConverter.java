package com.kiri.server.domain.taste.converter;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.taste.dto.TasteProfileDTO;
import com.kiri.server.domain.taste.dto.TasteResDTO;
import com.kiri.server.domain.taste.entity.MemberTaste;
import com.kiri.server.domain.taste.enums.DurationLevel;

import java.util.List;

public class TasteConverter {

    public static MemberTaste toMemberTaste(Member member) {
        return MemberTaste.builder()
                .member(member)
                .build();
    }

    public static TasteResDTO.MyTaste toMyTaste(Member member, MemberTaste taste) {
        return TasteResDTO.MyTaste.builder()
                .nickname(member.getNickname())
                .isSet(true)
                .categories(taste.getCategoryValues())
                .indoorOutdoor(taste.getIndoorOutdoor())
                .activityLevel(taste.getActivityLevel())
                .priceLevel(taste.getPriceLevel())
                .durationLevel(taste.getDurationLevel())
                .build();
    }

    public static TasteResDTO.MyTaste toEmptyMyTaste(Member member) {
        return TasteResDTO.MyTaste.builder()
                .nickname(member.getNickname())
                .isSet(false)
                .categories(List.of())
                .build();
    }

    public static TasteProfileDTO.MemberProfile toMemberProfile(Long memberId, MemberTaste taste) {
        DurationLevel durationLevel = taste.getDurationLevel();
        return TasteProfileDTO.MemberProfile.builder()
                .memberId(memberId)
                .isSet(true)
                .categories(taste.getCategoryValues())
                .indoorOutdoor(taste.getIndoorOutdoor())
                .activityLevel(taste.getActivityLevel())
                .priceLevel(taste.getPriceLevel())
                .durationLevel(durationLevel)
                .durationMinMinutes(durationLevel == null ? null : durationLevel.getMinMinutes())
                .durationMaxMinutes(durationLevel == null ? null : durationLevel.getMaxMinutes())
                .build();
    }

    public static TasteProfileDTO.MemberProfile toEmptyMemberProfile(Long memberId) {
        return TasteProfileDTO.MemberProfile.builder()
                .memberId(memberId)
                .isSet(false)
                .categories(List.of())
                .build();
    }

    public static TasteProfileDTO.FamilyProfile toFamilyProfile(Long familyId, List<TasteProfileDTO.MemberProfile> members) {
        return TasteProfileDTO.FamilyProfile.builder()
                .familyId(familyId)
                .members(members)
                .setMemberCount((int) members.stream().filter(TasteProfileDTO.MemberProfile::isSet).count())
                .build();
    }
}
