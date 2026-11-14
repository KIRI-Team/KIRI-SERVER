package com.kiri.server.domain.suggestion.converter;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.suggestion.dto.SuggestionReqDTO;
import com.kiri.server.domain.suggestion.dto.SuggestionResDTO;
import com.kiri.server.domain.suggestion.entity.Suggestion;

public class SuggestionConverter {

    public static Suggestion toSuggestion(Member member, SuggestionReqDTO.Create request) {
        return Suggestion.builder()
                .member(member)
                .content(request.content())
                .imageKey(request.imageKey())
                .build();
    }

    public static SuggestionResDTO.Create toCreateResponse(Suggestion suggestion) {
        return SuggestionResDTO.Create.builder()
                .suggestionId(suggestion.getId())
                .build();
    }
}
