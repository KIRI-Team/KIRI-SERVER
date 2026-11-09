package com.kiri.server.domain.suggestion.service;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.suggestion.dto.SuggestionReqDTO;
import com.kiri.server.domain.suggestion.dto.SuggestionResDTO;
import com.kiri.server.domain.suggestion.entity.Suggestion;
import com.kiri.server.domain.suggestion.repository.SuggestionRepository;
import com.kiri.server.global.apiPayload.exception.ProjectException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SuggestionServiceTest {

    @InjectMocks
    private SuggestionService suggestionService;

    @Mock
    private SuggestionRepository suggestionRepository;

    @Mock
    private MemberRepository memberRepository;

    private Member member;

    @BeforeEach
    void setUp() {
        member = Member.builder()
                .email("test@test.com")
                .build();
    }

    @Test
    @DisplayName("건의 작성에 성공한다.")
    void createSuggestionSuccess() {
        Long memberId = 1L;

        SuggestionReqDTO.Create request = new SuggestionReqDTO.Create("서비스 개선 요청입니다.", "suggestions/test.png");

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        given(suggestionRepository.save(any(Suggestion.class))).willAnswer(invocation -> {Suggestion suggestion = invocation.getArgument(0);ReflectionTestUtils.setField(suggestion, "id", 1L);return suggestion;});

        SuggestionResDTO.Create response = suggestionService.createSuggestion(memberId, request);

        assertThat(response.suggestionId()).isEqualTo(1L);

        then(memberRepository).should().findById(memberId);
        then(suggestionRepository).should().save(any(Suggestion.class));
    }

    @Test
    @DisplayName("이미지 없이 건의 작성에 성공한다.")
    void createSuggestionWithoutImageSuccess() {
        Long memberId = 1L;

        SuggestionReqDTO.Create request = new SuggestionReqDTO.Create("이미지 없는 건의입니다.", null);

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        given(suggestionRepository.save(any(Suggestion.class))).willAnswer(invocation -> {Suggestion suggestion = invocation.getArgument(0);ReflectionTestUtils.setField(suggestion, "id", 2L);return suggestion;});

        SuggestionResDTO.Create response = suggestionService.createSuggestion(memberId, request);

        assertThat(response.suggestionId()).isEqualTo(2L);

        then(suggestionRepository).should().save(any(Suggestion.class));
    }

    @Test
    @DisplayName("존재하지 않는 회원은 건의를 작성할 수 없다.")
    void createSuggestionWithNonExistentMember() {
        Long memberId = 999L;

        SuggestionReqDTO.Create request = new SuggestionReqDTO.Create("건의 내용입니다.", null);

        given(memberRepository.findById(memberId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> suggestionService.createSuggestion(memberId, request)).isInstanceOf(ProjectException.class);

        then(suggestionRepository).shouldHaveNoInteractions();
    }
}