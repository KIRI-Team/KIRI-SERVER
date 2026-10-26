package com.kiri.server.domain.member.converter;

import com.kiri.server.domain.member.dto.MemberDeviceReqDTO;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.entity.MemberDevice;

public class MemberDeviceConverter {

    public static MemberDevice toMemberDevice(Member member, MemberDeviceReqDTO.Register dto) {
        return MemberDevice.builder()
                .member(member)
                .fcmToken(dto.fcmToken())
                .deviceType(dto.deviceType())
                .deviceIdentifier(dto.deviceIdentifier())
                .build();
    }
}
