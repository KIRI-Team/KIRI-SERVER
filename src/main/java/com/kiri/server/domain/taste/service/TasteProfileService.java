package com.kiri.server.domain.taste.service;

import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.taste.converter.TasteConverter;
import com.kiri.server.domain.taste.dto.TasteProfileDTO;
import com.kiri.server.domain.taste.entity.MemberTaste;
import com.kiri.server.domain.taste.repository.MemberTasteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TasteProfileService {

    private final MemberRepository memberRepository;
    private final MemberTasteRepository memberTasteRepository;

    @Transactional(readOnly = true)
    public TasteProfileDTO.MemberProfile getMemberProfile(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new MemberException(MemberErrorCode.NOT_FOUND);
        }

        return memberTasteRepository.findWithCategoriesByMemberId(memberId)
                .map(taste -> TasteConverter.toMemberProfile(memberId, taste))
                .orElseGet(() -> TasteConverter.toEmptyMemberProfile(memberId));
    }

    @Transactional(readOnly = true)
    public TasteProfileDTO.FamilyProfile getFamilyProfile(Long familyId) {
        List<Long> memberIds = memberTasteRepository.findMemberIdsByFamilyId(familyId);
        if (memberIds.isEmpty()) {
            return TasteConverter.toFamilyProfile(familyId, List.of());
        }

        Map<Long, MemberTaste> tastes = memberTasteRepository.findAllWithCategoriesByMemberIdIn(memberIds).stream()
                .collect(Collectors.toMap(taste -> taste.getMember().getId(), Function.identity()));

        List<TasteProfileDTO.MemberProfile> members = memberIds.stream()
                .map(memberId -> tastes.containsKey(memberId)
                        ? TasteConverter.toMemberProfile(memberId, tastes.get(memberId))
                        : TasteConverter.toEmptyMemberProfile(memberId))
                .toList();

        return TasteConverter.toFamilyProfile(familyId, members);
    }
}
