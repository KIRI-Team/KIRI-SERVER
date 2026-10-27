package com.kiri.server.domain.family.converter;

import com.kiri.server.domain.family.dto.FamilyResDTO;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;

import java.time.LocalDateTime;

public class FamilyConverter {

    public static Family toFamily(String name, Member member, String inviteCode, String inviteToken, LocalDateTime inviteExpiresAt){
        return Family.builder()
                .name(name)
                .owner(member)
                .inviteCode(inviteCode)
                .inviteToken(inviteToken)
                .inviteExpiresAt(inviteExpiresAt)
                .build();
    }

    public static FamilyResDTO.Create toCreateResponse(Family family, String inviteUrl) {
        return FamilyResDTO.Create.builder()
                .familyId(family.getId())
                .name(family.getName())
                .inviteCode(family.getInviteCode())
                .inviteUrl(inviteUrl)
                .inviteExpiresAt(family.getInviteExpiresAt())
                .build();
    }

    public static FamilyResDTO.Invite toInviteResponse(Family family, String inviteUrl) {
        return FamilyResDTO.Invite.builder()
                .inviteCode(family.getInviteCode())
                .inviteUrl(inviteUrl)
                .inviteExpiresAt(family.getInviteExpiresAt())
                .build();
    }
}
