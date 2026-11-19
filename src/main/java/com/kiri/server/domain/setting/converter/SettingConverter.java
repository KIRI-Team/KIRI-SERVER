package com.kiri.server.domain.setting.converter;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.setting.entity.MemberSetting;

public class SettingConverter {

    public static MemberSetting toMemberSetting(Member member) {
        return MemberSetting.builder()
                .member(member)
                .build();
    }
}
