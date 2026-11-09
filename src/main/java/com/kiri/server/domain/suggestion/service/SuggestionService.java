package com.kiri.server.domain.suggestion.service;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.suggestion.converter.SuggestionConverter;
import com.kiri.server.domain.suggestion.dto.SuggestionReqDTO;
import com.kiri.server.domain.suggestion.dto.SuggestionResDTO;
import com.kiri.server.domain.suggestion.entity.Suggestion;
import com.kiri.server.domain.suggestion.repository.SuggestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final SuggestionRepository suggestionRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public SuggestionResDTO.Create createSuggestion(Long memberId, SuggestionReqDTO.Create dto) {
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));

        Suggestion suggestion = SuggestionConverter.toSuggestion(member, dto);

        Suggestion savedSuggestion = suggestionRepository.save(suggestion);

        return SuggestionConverter.toCreateResponse(savedSuggestion);
    }
}
