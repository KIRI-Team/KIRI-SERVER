package com.kiri.server.domain.family.converter;

import com.kiri.server.domain.family.dto.FamilyResDTO;
import com.kiri.server.domain.family.entity.Family;

public class FamilyConverter {

    public static FamilyResDTO.Create toCreateResponse(Family family, String inviteUrl) {
        return FamilyResDTO.Create.builder()
                .familyId(family.getId())
                .name(family.getName())
                .inviteCode(inviteUrl)
                .build();
    }
}
