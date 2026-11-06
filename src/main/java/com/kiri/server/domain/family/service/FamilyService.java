package com.kiri.server.domain.family.service;

import com.kiri.server.domain.family.converter.FamilyConverter;
import com.kiri.server.domain.family.dto.FamilyReqDTO;
import com.kiri.server.domain.family.dto.FamilyResDTO;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.family.event.FamilyCreatedEvent;
import com.kiri.server.domain.family.exception.FamilyException;
import com.kiri.server.domain.family.exception.code.FamilyErrorCode;
import com.kiri.server.domain.family.repository.FamilyRepository;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.global.apiPayload.exception.ProjectException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FamilyService {

    private final FamilyRepository familyRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public FamilyResDTO.Create createFamily(Long memberId, FamilyReqDTO.Create dto) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new ProjectException(MemberErrorCode.NOT_FOUND));

        if (familyRepository.existsByName(dto.name())) {
            throw new ProjectException(FamilyErrorCode.DUPLICATE_NAME);
        }

        String inviteCode = generateInviteCode();
        String inviteToken = generateInviteToken();
        LocalDateTime inviteExpiresAt = LocalDateTime.now().plusDays(7);

        Family family = FamilyConverter.toFamily(dto.name(), member, inviteCode, inviteToken, inviteExpiresAt);

        familyRepository.save(family);

        member.assignFamily(family);
        member.joinFamily(family);

        eventPublisher.publishEvent(new FamilyCreatedEvent(family.getId()));

        String inviteUrl = generateInviteUrl(inviteToken);

        return FamilyConverter.toCreateResponse(family, inviteUrl);
    }

    @Transactional
    public FamilyResDTO.Invite regenerateInvite(Long memberId, Long familyId) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));

        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.NOT_FOUND));

        if (member.getFamily() == null || !family.getId().equals(member.getFamily().getId())) {
            throw new ProjectException(FamilyErrorCode.FORBIDDEN);
        }

        String inviteCode = generateInviteCode();
        String inviteToken = generateInviteToken();
        LocalDateTime inviteExpiresAt = LocalDateTime.now().plusDays(7);

        family.renewInvite(inviteCode, inviteToken, inviteExpiresAt);

        String inviteUrl = generateInviteUrl(inviteToken);

        return FamilyConverter.toInviteResponse(family, inviteUrl);
    }

    @Transactional
    public void deleteInvite(Long memberId, Long familyId) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));

        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.NOT_FOUND));

        if (member.getFamily() == null || !family.getId().equals(member.getFamily().getId())) {
            throw new ProjectException(FamilyErrorCode.FORBIDDEN);
        }

        family.clearInviteInfo();
    }

    @Transactional(readOnly = true)
    public FamilyResDTO.Invite getInvite(Long memberId, Long familyId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));

        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.NOT_FOUND));

        if (member.getFamily() == null || !family.getId().equals(member.getFamily().getId())) {
            throw new ProjectException(FamilyErrorCode.FORBIDDEN);
        }

        if (family.getInviteCode() == null || family.getInviteToken() == null || family.getInviteExpiresAt() == null) {
            return null;
        }

        String inviteUrl = generateInviteUrl(family.getInviteToken());

        return FamilyConverter.toInviteResponse(family, inviteUrl);
    }

    private String generateInviteCode() {

        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom random = new SecureRandom();
        String code;

        do {
            StringBuilder builder = new StringBuilder(6);

            for (int i = 0; i < 6; i++) {
                builder.append(characters.charAt(random.nextInt(characters.length())));
            }

            code = builder.toString();

        } while (familyRepository.existsByInviteCode(code));

        return code;
    }

    private String generateInviteToken() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "");
    }

    private String generateInviteUrl(String inviteToken) {
        return "https://kiri.app/invite/" + inviteToken;
    }

    @Transactional
    public FamilyResDTO.UpdateName updateFamilyName(Long memberId, Long familyId, FamilyReqDTO.UpdateName request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));

        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.NOT_FOUND));

        if (family.getOwner() == null || !family.getOwner().getId().equals(member.getId())) {
            throw new FamilyException(FamilyErrorCode.FORBIDDEN);
        }

        String normalizedName = normalizeFamilyName(request.name());

        if (family.getName().equals(normalizedName)) {
            return FamilyConverter.toUpdateNameResponse(family);
        }

        if (familyRepository.existsByName(normalizedName)) {
            throw new FamilyException(FamilyErrorCode.DUPLICATE_NAME);
        }

        family.updateName(normalizedName);

        return FamilyConverter.toUpdateNameResponse(family);
    }

    @Transactional
    public FamilyResDTO.UpdateAddress updateFamilyAddress(Long memberId, Long familyId, FamilyReqDTO.UpdateAddress request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));

        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.NOT_FOUND));

        if (member.getFamily() == null || !family.getId().equals(member.getFamily().getId())) {
            throw new FamilyException(FamilyErrorCode.FORBIDDEN);
        }

        family.updateAddress(request.postalCode(), request.address(), request.detailAddress(), request.regionCode(), request.latitude(), request.longitude());

        return FamilyConverter.toUpdateAddressResponse(family);
    }

    private String normalizeFamilyName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }
}
