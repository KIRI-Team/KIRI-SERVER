package com.kiri.server.domain.family.service;

import com.kiri.server.domain.family.dto.FamilyReqDTO;
import com.kiri.server.domain.family.dto.FamilyResDTO;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.family.event.FamilyCreatedEvent;
import com.kiri.server.domain.family.exception.FamilyException;
import com.kiri.server.domain.family.exception.code.FamilyErrorCode;
import com.kiri.server.domain.family.repository.FamilyRepository;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.global.apiPayload.exception.ProjectException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FamilyServiceTest {

    @Mock
    private FamilyRepository familyRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FamilyService familyService;

    private Member member;
    private Family family;

    @BeforeEach
    void setUp() {
        member = mock(Member.class);
        family = mock(Family.class);
    }

    @Test
    @DisplayName("가족 공간 생성 완료")
    void createFamilySuccess() {
        Long memberId = 1L;
        FamilyReqDTO.Create dto = new FamilyReqDTO.Create("끼리네 가족");
        Member member = mock(Member.class);

        when(memberRepository.findByIdForUpdate(memberId))
                .thenReturn(Optional.of(member));

        when(familyRepository.existsByName(dto.name()))
                .thenReturn(false);

        when(familyRepository.existsByInviteCode(anyString()))
                .thenReturn(false);

        when(familyRepository.save(any(Family.class)))
                .thenAnswer(invocation -> {
                    Family family = invocation.getArgument(0);
                    ReflectionTestUtils.setField(family, "id", 1L);
                    return family;
                });

        FamilyResDTO.Create response =
                familyService.createFamily(memberId, dto);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("끼리네 가족");
        assertThat(response.familyId()).isEqualTo(1L);
        assertThat(response.inviteCode()).isNotNull();
        assertThat(response.inviteCode()).hasSize(6);
        assertThat(response.inviteUrl()).startsWith("https://kiri.app/invite/");
        assertThat(response.inviteExpiresAt()).isNotNull();

        verify(memberRepository).findByIdForUpdate(memberId);
        verify(familyRepository).existsByName(dto.name());
        verify(familyRepository).save(any(Family.class));
        verify(member).assignFamily(any(Family.class));
        verify(eventPublisher).publishEvent(new FamilyCreatedEvent(1L));
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 가족 공간 생성 실패")
    void createFamilyMemberNotFound() {
        Long memberId = 999L;
        FamilyReqDTO.Create dto = new FamilyReqDTO.Create("끼리네 가족");

        when(memberRepository.findByIdForUpdate(memberId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                familyService.createFamily(memberId, dto)
        )
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.NOT_FOUND);

        verify(memberRepository).findByIdForUpdate(memberId);
        verify(familyRepository, never()).existsByName(anyString());
        verify(familyRepository, never()).save(any(Family.class));
    }

    @Test
    @DisplayName("이미 존재하는 가족명이면 가족 공간 생성 실패")
    void createFamilyDuplicateName() {
        Long memberId = 1L;
        FamilyReqDTO.Create dto = new FamilyReqDTO.Create("끼리네 가족");
        Member member = mock(Member.class);

        when(memberRepository.findByIdForUpdate(memberId))
                .thenReturn(Optional.of(member));

        when(familyRepository.existsByName(dto.name()))
                .thenReturn(true);

        assertThatThrownBy(() ->
                familyService.createFamily(memberId, dto)
        )
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(FamilyErrorCode.DUPLICATE_NAME);

        verify(memberRepository).findByIdForUpdate(memberId);
        verify(familyRepository).existsByName(dto.name());
        verify(familyRepository, never()).save(any(Family.class));
        verify(member, never()).assignFamily(any(Family.class));
    }

    @Test
    @DisplayName("가족 공간 생성 시 초대 코드와 초대 정보가 정상적으로 생성")
    void createFamilyGenerateInviteInfo() {
        Long memberId = 1L;
        FamilyReqDTO.Create dto = new FamilyReqDTO.Create("끼리네 가족");
        Member member = mock(Member.class);

        when(memberRepository.findByIdForUpdate(memberId))
                .thenReturn(Optional.of(member));

        when(familyRepository.existsByName(dto.name()))
                .thenReturn(false);

        when(familyRepository.existsByInviteCode(anyString()))
                .thenReturn(false);

        when(familyRepository.save(any(Family.class)))
                .thenAnswer(invocation -> {
                    Family family = invocation.getArgument(0);
                    ReflectionTestUtils.setField(family, "id", 1L);
                    return family;
                });

        ArgumentCaptor<Family> familyCaptor =
                ArgumentCaptor.forClass(Family.class);

        FamilyResDTO.Create response = familyService.createFamily(memberId, dto);

        verify(familyRepository).save(familyCaptor.capture());

        Family family = familyCaptor.getValue();

        assertThat(family.getName()).isEqualTo("끼리네 가족");
        assertThat(family.getOwner()).isEqualTo(member);

        assertThat(family.getInviteCode())
                .isNotNull()
                .hasSize(6);

        assertThat(family.getInviteToken())
                .isNotNull()
                .hasSize(32);

        assertThat(family.getInviteExpiresAt())
                .isAfter(LocalDateTime.now().plusDays(6))
                .isBefore(LocalDateTime.now().plusDays(8));

        assertThat(response.inviteCode())
                .isEqualTo(family.getInviteCode());

        assertThat(response.inviteUrl())
                .isEqualTo("https://kiri.app/invite/" + family.getInviteToken());

        verify(eventPublisher).publishEvent(new FamilyCreatedEvent(1L));
    }


    @Test
    @DisplayName("가족 구성원이 초대 정보를 생성할 수 있다.")
    void regenerateInviteSuccess() {
        Long memberId = 1L;
        Long familyId = 10L;

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));

        when(member.getFamily()).thenReturn(family);

        when(family.getId()).thenReturn(familyId);

        when(family.getInviteCode()).thenReturn("ABC123");

        when(family.getInviteExpiresAt()).thenReturn(LocalDateTime.now().plusDays(7));

        FamilyResDTO.Invite result = familyService.regenerateInvite(memberId, familyId);

        assertThat(result).isNotNull();
        assertThat(result.inviteCode())
                .hasSize(6)
                .matches("[A-Z0-9]+");
        assertThat(result.inviteUrl()).startsWith("https://kiri.app/invite/");
        assertThat(result.inviteExpiresAt()).isNotNull();

        verify(family).renewInvite(anyString(), anyString(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("기존 초대 정보가 있어도 새로운 초대 정보로 재생성한다.")
    void regenerateInviteReplacesExistingInvite() {
        Long memberId = 1L;
        Long familyId = 10L;

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));

        when(member.getFamily()).thenReturn(family);

        when(family.getId()).thenReturn(familyId);

        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);

        familyService.regenerateInvite(memberId, familyId);

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);

        ArgumentCaptor<LocalDateTime> expiresCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        verify(family).renewInvite(codeCaptor.capture(), tokenCaptor.capture(), expiresCaptor.capture());

        assertThat(codeCaptor.getValue()).hasSize(6);

        assertThat(tokenCaptor.getValue()).hasSize(32);

        assertThat(expiresCaptor.getValue()).isAfter(LocalDateTime.now().plusDays(6));
    }

    @Test
    @DisplayName("존재하지 않는 가족이면 예외가 발생한다.")
    void regenerateInviteFamilyNotFound() {
        Long memberId = 1L;
        Long familyId = 10L;

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        when(familyRepository.findById(familyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> familyService.regenerateInvite(memberId, familyId))
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(FamilyErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("가족 구성원이 아니면 예외가 발생한다.")
    void regenerateInviteForbidden() {
        Long memberId = 1L;
        Long familyId = 10L;

        Member anotherMember = mock(Member.class);
        Family anotherFamily = mock(Family.class);

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));

        when(member.getFamily()).thenReturn(anotherFamily);

        when(family.getId()).thenReturn(familyId);

        when(anotherFamily.getId()).thenReturn(20L);

        assertThatThrownBy(() -> familyService.regenerateInvite(memberId, familyId))
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(FamilyErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("가족 구성원이 초대 정보를 삭제할 수 있다.")
    void deleteInviteSuccess() {
        Long memberId = 1L;
        Long familyId = 10L;

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));

        when(member.getFamily()).thenReturn(family);

        when(family.getId()).thenReturn(familyId);

        familyService.deleteInvite(memberId, familyId);

        verify(family).clearInviteInfo();
    }

    @Test
    @DisplayName("존재하지 않는 가족이면 초대 정보를 삭제할 수 없다.")
    void deleteInviteFamilyNotFound() {
        Long memberId = 1L;
        Long familyId = 10L;

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        when(familyRepository.findById(familyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> familyService.deleteInvite(memberId, familyId))
                .isInstanceOf(FamilyException.class)
                .extracting("errorCode")
                .isEqualTo(FamilyErrorCode.NOT_FOUND);

        verify(family, never()).clearInviteInfo();
    }

    @Test
    @DisplayName("가족 구성원이 아니면 초대 정보를 삭제할 수 없다.")
    void deleteInviteForbidden() {
        Long memberId = 1L;
        Long familyId = 10L;

        Family anotherFamily = mock(Family.class);

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));

        when(member.getFamily()).thenReturn(anotherFamily);

        when(family.getId()).thenReturn(familyId);

        when(anotherFamily.getId()).thenReturn(20L);

        assertThatThrownBy(() -> familyService.deleteInvite(memberId, familyId))
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(FamilyErrorCode.FORBIDDEN);
        verify(family, never()).clearInviteInfo();
    }
}