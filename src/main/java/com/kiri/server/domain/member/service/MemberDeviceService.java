package com.kiri.server.domain.member.service;

import com.kiri.server.domain.member.converter.MemberDeviceConverter;
import com.kiri.server.domain.member.dto.MemberDeviceReqDTO;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.entity.MemberDevice;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberDeviceErrorCode;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberDeviceRepository;
import com.kiri.server.domain.member.repository.MemberRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberDeviceService {

    private final MemberRepository memberRepository;
    private final MemberDeviceRepository memberDeviceRepository;

    @Transactional
    public void registerDevice(Long memberId, MemberDeviceReqDTO.Register dto) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));

        MemberDevice memberDevice = memberDeviceRepository.findByMemberAndDeviceIdentifier(member, dto.deviceIdentifier()).orElse(null);

        if (memberDevice != null) {
            memberDevice.update(dto.fcmToken(), dto.deviceType());
            return;
        }

        memberDeviceRepository.save(MemberDeviceConverter.toMemberDevice(member, dto));
    }

    @Transactional
    public void deleteDevice(Long memberId, String deviceIdentifier) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));

        MemberDevice memberDevice = memberDeviceRepository.findByMemberAndDeviceIdentifier(member, deviceIdentifier)
                .orElseThrow(() -> new MemberException(MemberDeviceErrorCode.DEVICE_NOT_FOUND));

        memberDeviceRepository.delete(memberDevice);
    }
}
