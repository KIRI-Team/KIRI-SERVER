package com.kiri.server.domain.taste.service;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.taste.converter.TasteConverter;
import com.kiri.server.domain.taste.dto.TasteReqDTO;
import com.kiri.server.domain.taste.dto.TasteResDTO;
import com.kiri.server.domain.taste.entity.MemberTaste;
import com.kiri.server.domain.taste.event.MemberTasteUpdatedEvent;
import com.kiri.server.domain.taste.exception.TasteException;
import com.kiri.server.domain.taste.exception.code.TasteErrorCode;
import com.kiri.server.domain.taste.repository.MemberTasteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class TasteService {

    private final MemberRepository memberRepository;
    private final MemberTasteRepository memberTasteRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public TasteResDTO.MyTaste getMyTaste(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));

        return memberTasteRepository.findWithCategoriesByMemberId(memberId)
                .map(taste -> TasteConverter.toMyTaste(member, taste))
                .orElseGet(() -> TasteConverter.toEmptyMyTaste(member));
    }

    @Transactional
    public TasteResDTO.MyTaste saveMyTaste(Long memberId, TasteReqDTO.Save dto) {
        validate(dto);

        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));

        MemberTaste taste = memberTasteRepository.findWithCategoriesByMemberId(memberId)
                .orElseGet(() -> TasteConverter.toMemberTaste(member));
        taste.update(dto.indoorOutdoor(), dto.activityLevel(), dto.priceLevel(), dto.durationLevel(), dto.categories());
        memberTasteRepository.save(taste);

        Long familyId = member.getFamily() == null ? null : member.getFamily().getId();
        eventPublisher.publishEvent(new MemberTasteUpdatedEvent(memberId, familyId));

        return TasteConverter.toMyTaste(member, taste);
    }

    @Transactional
    public void deleteTaste(Long memberId) {
        memberTasteRepository.findWithCategoriesByMemberId(memberId)
                .ifPresent(memberTasteRepository::delete);
    }

    private void validate(TasteReqDTO.Save dto) {
        if (dto.indoorOutdoor() == IndoorOutdoor.BOTH) {
            throw new TasteException(TasteErrorCode.INVALID_INDOOR_OUTDOOR);
        }
        if (new HashSet<>(dto.categories()).size() != dto.categories().size()) {
            throw new TasteException(TasteErrorCode.DUPLICATE_CATEGORY);
        }
    }
}
