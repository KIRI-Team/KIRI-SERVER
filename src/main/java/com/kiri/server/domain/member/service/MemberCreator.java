package com.kiri.server.domain.member.service;

import com.kiri.server.domain.member.converter.MemberConverter;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.enums.SocialProvider;
import com.kiri.server.domain.member.oauth.OidcUserInfo;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.setting.converter.SettingConverter;
import com.kiri.server.domain.setting.entity.MemberSetting;
import com.kiri.server.domain.setting.repository.MemberSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MemberCreator {

    private final MemberRepository memberRepository;
    private final MemberSettingRepository memberSettingRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Member create(SocialProvider socialProvider, OidcUserInfo oidcUserInfo) {
        Member member = MemberConverter.toMember(socialProvider, oidcUserInfo);
        memberRepository.save(member);

        MemberSetting memberSetting = SettingConverter.toMemberSetting(member);
        memberSettingRepository.save(memberSetting);

        return member;
    }
}
