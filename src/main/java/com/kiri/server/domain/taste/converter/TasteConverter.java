package com.kiri.server.domain.taste.converter;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.taste.dto.TasteResDTO;
import com.kiri.server.domain.taste.entity.MemberTaste;

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
}
