package com.kiri.server.domain.family.service;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
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
        verify(member).joinFamily(any(Family.class));
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
        verify(member, never()).joinFamily(any(Family.class));
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

    @Test
    @DisplayName("가족 구성원이 초대 정보를 조회할 수 있다.")
    void getInviteSuccess() {
        Long memberId = 1L;
        Long familyId = 10L;

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));

        when(member.getFamily()).thenReturn(family);

        when(family.getId()).thenReturn(familyId);

        when(family.getInviteCode()).thenReturn("ABC123");

        when(family.getInviteToken()).thenReturn("test-token");

        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);

        when(family.getInviteExpiresAt()).thenReturn(expiresAt);

        FamilyResDTO.Invite result = familyService.getInvite(memberId, familyId);

        assertThat(result).isNotNull();
        assertThat(result.inviteCode())
                .isEqualTo("ABC123");
        assertThat(result.inviteUrl())
                .isEqualTo("https://kiri.app/invite/test-token");
        assertThat(result.inviteExpiresAt())
                .isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("가족 구성원이 아니면 초대 정보를 조회할 수 없다.")
    void getInviteForbidden() {
        Long memberId = 1L;
        Long familyId = 10L;

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));

        when(member.getFamily()).thenReturn(null);

        assertThatThrownBy(() -> familyService.getInvite(memberId, familyId))
                .isInstanceOf(ProjectException.class);
    }

    @Test
    @DisplayName("존재하지 않는 가족이면 초대 정보를 조회할 수 없다.")
    void getInviteFamilyNotFound() {
        Long memberId = 1L;
        Long familyId = 10L;

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        when(familyRepository.findById(familyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> familyService.getInvite(memberId, familyId))
                .isInstanceOf(FamilyException.class);
    }

    @Test
    @DisplayName("초대 정보가 없으면 null을 반환한다.")
    void getInviteNoInviteInfo() {
        Long memberId = 1L;
        Long familyId = 10L;

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));

        when(member.getFamily()).thenReturn(family);

        when(family.getId()).thenReturn(familyId);

        when(family.getInviteCode()).thenReturn(null);

        FamilyResDTO.Invite result = familyService.getInvite(memberId, familyId);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("가족명을 정상적으로 변경한다.")
    void updateFamilyNameSuccess() {
        Long memberId = 1L;
        Long familyId = 1L;

        Member member = mock(Member.class);
        Family family = mock(Family.class);

        FamilyReqDTO.UpdateName request = new FamilyReqDTO.UpdateName("새로운 가족");

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));

        when(member.getId()).thenReturn(memberId);
        when(family.getOwner()).thenReturn(member);
        when(family.getName()).thenReturn("기존 가족");
        when(family.getId()).thenReturn(familyId);

        when(familyRepository.existsByName("새로운 가족")).thenReturn(false);

        FamilyResDTO.UpdateName result = familyService.updateFamilyName(memberId, familyId, request);

        verify(family).updateName("새로운 가족");

        assertThat(result).isNotNull();
        assertThat(result.familyId()).isEqualTo(familyId);
    }

    @Test
    @DisplayName("owner가 아닌 사용자는 가족명을 변경할 수 없다.")
    void updateFamilyNameForbidden() {
        Long memberId = 1L;
        Long ownerId = 2L;
        Long familyId = 1L;

        Member member = mock(Member.class);
        Member owner = mock(Member.class);
        Family family = mock(Family.class);

        FamilyReqDTO.UpdateName request = new FamilyReqDTO.UpdateName("새로운 가족");

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));

        when(member.getId()).thenReturn(memberId);
        when(owner.getId()).thenReturn(ownerId);
        when(family.getOwner()).thenReturn(owner);

        assertThatThrownBy(() -> familyService.updateFamilyName(memberId, familyId, request))
                .isInstanceOf(ProjectException.class)
                .hasMessage(FamilyErrorCode.FORBIDDEN.getMessage());

        verify(family, never()).updateName(anyString());
        verify(familyRepository, never()).existsByName(anyString());
    }

    @Test
    @DisplayName("존재하지 않는 가족의 이름은 변경할 수 없다.")
    void updateFamilyNameFamilyNotFound() {
        Long memberId = 1L;
        Long familyId = 999L;

        Member member = mock(Member.class);

        FamilyReqDTO.UpdateName request = new FamilyReqDTO.UpdateName("새로운 가족");

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(familyRepository.findById(familyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> familyService.updateFamilyName(memberId, familyId, request))
                .isInstanceOf(FamilyException.class)
                .hasMessage(FamilyErrorCode.NOT_FOUND.getMessage());

        verify(familyRepository, never()).existsByName(anyString());
    }

    @Test
    @DisplayName("이미 사용 중인 가족명으로 변경할 수 없다.")
    void updateFamilyNameDuplicate() {
        Long memberId = 1L;
        Long familyId = 1L;

        Member member = mock(Member.class);
        Family family = mock(Family.class);

        FamilyReqDTO.UpdateName request = new FamilyReqDTO.UpdateName("이미 존재하는 가족");

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));

        when(member.getId()).thenReturn(memberId);
        when(family.getOwner()).thenReturn(member);
        when(family.getName()).thenReturn("기존 가족");

        when(familyRepository.existsByName("이미 존재하는 가족")).thenReturn(true);

        assertThatThrownBy(() -> familyService.updateFamilyName(memberId, familyId, request))
                .isInstanceOf(FamilyException.class)
                .hasMessage(FamilyErrorCode.DUPLICATE_NAME.getMessage());

        verify(family, never()).updateName(anyString());
    }

    @Test
    @DisplayName("가족명의 앞뒤 공백과 연속된 공백을 정규화한다.")
    void updateFamilyNameNormalize() {
        Long memberId = 1L;
        Long familyId = 1L;

        Member member = mock(Member.class);
        Family family = mock(Family.class);

        FamilyReqDTO.UpdateName request = new FamilyReqDTO.UpdateName("  우리    가족  ");

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));

        when(member.getId()).thenReturn(memberId);
        when(family.getOwner()).thenReturn(member);
        when(family.getName()).thenReturn("기존 가족");

        when(familyRepository.existsByName("우리 가족")).thenReturn(false);

        FamilyResDTO.UpdateName result = familyService.updateFamilyName(memberId, familyId, request);

        verify(family).updateName("우리 가족");
    }

    @Test
    @DisplayName("존재하지 않는 회원이 주소 설정을 요청하면 예외가 발생한다.")
    void updateFamilyAddressMemberNotFound() {
        Long memberId = 1L;
        Long familyId = 10L;

        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        FamilyReqDTO.UpdateAddress request = createAddressRequest();

        assertThatThrownBy(() -> familyService.updateFamilyAddress(memberId, familyId, request))
                .isInstanceOf(MemberException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.NOT_FOUND);

        verify(familyRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("가족 주소 설정 성공")
    void updateFamilyAddressSuccess() {
        Long memberId = 1L;
        Long familyId = 10L;

        Member member = mock(Member.class);

        Family family = Family.builder().id(familyId).build();

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));
        when(member.getFamily()).thenReturn(family);

        FamilyReqDTO.UpdateAddress request = createAddressRequest();

        FamilyResDTO.UpdateAddress result = familyService.updateFamilyAddress(memberId, familyId, request);

        assertThat(family.getPostalCode()).isEqualTo("12345");
        assertThat(family.getAddress()).isEqualTo("서울특별시 마포구 양화로 123");
        assertThat(family.getDetailAddress()).isEqualTo("101동 202호");
        assertThat(family.getRegionCode()).isEqualTo("1144012300");
        assertThat(family.getLatitude()).isEqualByComparingTo("37.5563");
        assertThat(family.getLongitude()).isEqualByComparingTo("126.9220");

        assertThat(result.familyId()).isEqualTo(familyId);
        assertThat(result.postalCode()).isEqualTo("12345");
        assertThat(result.address()).isEqualTo("서울특별시 마포구 양화로 123");
        assertThat(result.detailAddress()).isEqualTo("101동 202호");
        assertThat(result.regionCode()).isEqualTo("1144012300");
        assertThat(result.latitude()).isEqualByComparingTo("37.5563");
        assertThat(result.longitude()).isEqualByComparingTo("126.9220");

        verify(memberRepository).findById(memberId);
        verify(familyRepository).findById(familyId);
    }

    @Test
    @DisplayName("존재하지 않는 가족의 주소 설정을 요청하면 예외가 발생한다.")
    void updateFamilyAddressFamilyNotFound() {
        Long memberId = 1L;
        Long familyId = 10L;

        Member member = mock(Member.class);

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(familyRepository.findById(familyId)).thenReturn(Optional.empty());

        FamilyReqDTO.UpdateAddress request = createAddressRequest();

        assertThatThrownBy(() -> familyService.updateFamilyAddress(memberId, familyId, request))
                .isInstanceOf(FamilyException.class)
                .extracting("errorCode")
                .isEqualTo(FamilyErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("해당 가족의 구성원이 아니면 주소 설정을 요청할 수 없다.")
    void updateFamilyAddressNotFamilyMember() {
        Long memberId = 1L;
        Long familyId = 10L;

        Member member = mock(Member.class);

        Family family = Family.builder().id(familyId).build();

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));
        when(member.getFamily()).thenReturn(null);

        FamilyReqDTO.UpdateAddress request = createAddressRequest();

        assertThatThrownBy(() -> familyService.updateFamilyAddress(memberId, familyId, request))
                .isInstanceOf(FamilyException.class)
                .extracting("errorCode")
                .isEqualTo(FamilyErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("가족 ID 조회 성공")
    void getFamilyIdSuccess() {
        Long memberId = 1L;
        Long familyId = 10L;

        Family family = mock(Family.class);

        when(familyRepository.findByMemberId(memberId)).thenReturn(Optional.of(family));
        when(family.getId()).thenReturn(familyId);

        FamilyResDTO.Id result = familyService.getFamilyId(memberId);

        assertThat(result.familyId()).isEqualTo(familyId);

        verify(familyRepository).findByMemberId(memberId);
    }

    @Test
    @DisplayName("가족에 소속되지 않은 사용자가 가족 ID 조회 시 예외 발생")
    void getFamilyIdFailNotJoinedFamily() {
        Long memberId = 1L;

        when(familyRepository.findByMemberId(memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> familyService.getFamilyId(memberId))
                .isInstanceOf(FamilyException.class)
                .hasMessage(FamilyErrorCode.NOT_FOUND.getMessage());

        verify(familyRepository).findByMemberId(memberId);
    }

    @Test
    @DisplayName("유효한 초대 코드 입력 시 가족에 가입한다.")
    void joinFamilySuccessfully() {
        Long memberId = 1L;
        String inviteCode = "ABC123";

        Member member = Member.builder()
                .id(memberId)
                .build();

        Family family = Family.builder()
                .id(10L)
                .name("우리 가족")
                .inviteCode(inviteCode)
                .inviteExpiresAt(LocalDateTime.now().plusDays(7))
                .build();

        FamilyReqDTO.Join request = new FamilyReqDTO.Join(inviteCode);

        when(memberRepository.findByIdForUpdate(memberId)).thenReturn(Optional.of(member));
        when(familyRepository.findByInviteCode(inviteCode)).thenReturn(Optional.of(family));

        FamilyResDTO.Join response = familyService.joinFamily(memberId, request);

        assertEquals(family.getId(), response.familyId());
        assertEquals(family.getName(), response.familyName());

        assertSame(family, member.getFamily());
        assertNotNull(member.getFamilyJoinedAt());

        verify(memberRepository).findByIdForUpdate(memberId);
        verify(familyRepository).findByInviteCode(inviteCode);
    }

    @Test
    @DisplayName("존재하지 않는 초대 코드 입력 시 예외가 발생한다.")
    void joinFamilyWithInvalidInviteCode() {
        Long memberId = 1L;
        String inviteCode = "ABC123";

        Member member = Member.builder()
                .id(memberId)
                .build();

        FamilyReqDTO.Join request = new FamilyReqDTO.Join(inviteCode);

        when(memberRepository.findByIdForUpdate(memberId)).thenReturn(Optional.of(member));
        when(familyRepository.findByInviteCode(inviteCode)).thenReturn(Optional.empty());

        assertThrows(FamilyException.class, () -> familyService.joinFamily(memberId, request));

        assertNull(member.getFamily());
        verify(familyRepository).findByInviteCode(inviteCode);
    }

    @Test
    @DisplayName("만료된 초대 코드 입력 시 예외가 발생한다.")
    void joinFamilyWithExpiredInviteCode() {
        Long memberId = 1L;
        String inviteCode = "ABC123";

        Member member = Member.builder()
                .id(memberId)
                .build();

        Family family = Family.builder()
                .id(10L)
                .name("우리 가족")
                .inviteCode(inviteCode)
                .inviteExpiresAt(LocalDateTime.now().minusDays(1))
                .build();

        FamilyReqDTO.Join request = new FamilyReqDTO.Join(inviteCode);

        when(memberRepository.findByIdForUpdate(memberId)).thenReturn(Optional.of(member));
        when(familyRepository.findByInviteCode(inviteCode)).thenReturn(Optional.of(family));

        assertThrows(FamilyException.class, () -> familyService.joinFamily(memberId, request));

        assertNull(member.getFamily());
        assertNull(member.getFamilyJoinedAt());
    }

    @Test
    @DisplayName("이미 가족에 가입된 사용자는 다른 가족에 가입할 수 없다.")
    void joinFamilyWhenAlreadyJoined() {
        Long memberId = 1L;
        String inviteCode = "ABC123";

        Family existingFamily = Family.builder()
                .id(5L)
                .name("기존 가족")
                .build();

        Member member = Member.builder()
                .id(memberId)
                .family(existingFamily)
                .build();

        FamilyReqDTO.Join request = new FamilyReqDTO.Join(inviteCode);

        when(memberRepository.findByIdForUpdate(memberId)).thenReturn(Optional.of(member));

        assertThrows(FamilyException.class, () -> familyService.joinFamily(memberId, request));

        assertSame(existingFamily, member.getFamily());

        verify(familyRepository, never()).findByInviteCode(anyString());
    }

    @Test
    @DisplayName("유효한 초대 토큰으로 가족 정보를 조회한다.")
    void getInviteTokenInfoSuccessfully() {
        String inviteToken = "a1b2c3d4e5f678901234567890abcdef";

        Family family = Family.builder()
                .id(10L)
                .name("우리 가족")
                .inviteToken(inviteToken)
                .inviteExpiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(familyRepository.findByInviteToken(inviteToken)).thenReturn(Optional.of(family));

        FamilyResDTO.InviteTokenInfo response = familyService.getInviteTokenInfo(inviteToken);

        assertEquals(10L, response.familyId());
        assertEquals("우리 가족", response.familyName());

        verify(familyRepository).findByInviteToken(inviteToken);
    }

    @Test
    @DisplayName("존재하지 않는 초대 토큰으로 조회하면 예외가 발생한다.")
    void getInviteTokenInfoWithInvalidToken() {
        String inviteToken = "invalid-token";

        when(familyRepository.findByInviteToken(inviteToken)).thenReturn(Optional.empty());

        assertThrows(FamilyException.class, () -> familyService.getInviteTokenInfo(inviteToken));

        verify(familyRepository).findByInviteToken(inviteToken);
    }

    @Test
    @DisplayName("만료된 초대 토큰으로 조회하면 예외가 발생한다.")
    void getInviteTokenInfoWithExpiredToken() {
        String inviteToken = "a1b2c3d4e5f678901234567890abcdef";

        Family family = Family.builder()
                .id(10L)
                .name("우리 가족")
                .inviteToken(inviteToken)
                .inviteExpiresAt(LocalDateTime.now().minusDays(1))
                .build();

        when(familyRepository.findByInviteToken(inviteToken)).thenReturn(Optional.of(family));

        assertThrows(FamilyException.class, () -> familyService.getInviteTokenInfo(inviteToken));

        verify(familyRepository).findByInviteToken(inviteToken);
    }

    @Test
    @DisplayName("유효한 초대 토큰으로 가족 가입에 성공한다.")
    void joinFamilyByTokenSuccessfully() {
        Long memberId = 1L;
        String inviteToken = "a1b2c3d4e5f678901234567890abcdef";

        Member member = Member.builder()
                .id(memberId)
                .build();

        Family family = Family.builder()
                .id(10L)
                .name("우리 가족")
                .inviteToken(inviteToken)
                .inviteExpiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(memberRepository.findByIdForUpdate(memberId)).thenReturn(Optional.of(member));
        when(familyRepository.findByInviteToken(inviteToken)).thenReturn(Optional.of(family));

        FamilyResDTO.Join response = familyService.joinFamilyByToken(memberId, inviteToken);

        assertEquals(10L, response.familyId());
        assertEquals("우리 가족", response.familyName());

        assertSame(family, member.getFamily());
        assertNotNull(member.getFamilyJoinedAt());

        verify(memberRepository).findByIdForUpdate(memberId);
        verify(familyRepository).findByInviteToken(inviteToken);
    }

    @Test
    @DisplayName("존재하지 않는 초대 토큰으로 가입하면 예외가 발생한다.")
    void joinFamilyByTokenWithInvalidToken() {
        Long memberId = 1L;
        String inviteToken = "invalid-token";

        Member member = Member.builder()
                .id(memberId)
                .build();

        when(memberRepository.findByIdForUpdate(memberId)).thenReturn(Optional.of(member));
        when(familyRepository.findByInviteToken(inviteToken)).thenReturn(Optional.empty());

        assertThrows(FamilyException.class, () -> familyService.joinFamilyByToken(memberId, inviteToken));

        assertNull(member.getFamily());
        assertNull(member.getFamilyJoinedAt());
    }

    @Test
    @DisplayName("만료된 초대 토큰으로 가입하면 예외가 발생한다.")
    void joinFamilyByTokenWithExpiredToken() {
        Long memberId = 1L;
        String inviteToken = "a1b2c3d4e5f678901234567890abcdef";

        Member member = Member.builder()
                .id(memberId)
                .build();

        Family family = Family.builder()
                .id(10L)
                .name("우리 가족")
                .inviteToken(inviteToken)
                .inviteExpiresAt(LocalDateTime.now().minusDays(1))
                .build();

        when(memberRepository.findByIdForUpdate(memberId)).thenReturn(Optional.of(member));
        when(familyRepository.findByInviteToken(inviteToken)).thenReturn(Optional.of(family));

        assertThrows(FamilyException.class, () -> familyService.joinFamilyByToken(memberId, inviteToken));

        assertNull(member.getFamily());
        assertNull(member.getFamilyJoinedAt());
    }

    @Test
    @DisplayName("이미 가족에 가입된 사용자는 초대 토큰으로 가입할 수 없다.")
    void joinFamilyByTokenWhenAlreadyJoined() {
        Long memberId = 1L;
        String inviteToken = "a1b2c3d4e5f678901234567890abcdef";

        Family existingFamily = Family.builder()
                .id(5L)
                .name("기존 가족")
                .build();

        Member member = Member.builder()
                .id(memberId)
                .family(existingFamily)
                .build();

        when(memberRepository.findByIdForUpdate(memberId)).thenReturn(Optional.of(member));

        assertThrows(FamilyException.class, () -> familyService.joinFamilyByToken(memberId, inviteToken));

        assertSame(existingFamily, member.getFamily());

        verify(familyRepository, never()).findByInviteToken(anyString());
    }

    private FamilyReqDTO.UpdateAddress createAddressRequest() {
        return new FamilyReqDTO.UpdateAddress(
                "12345",
                "서울특별시 마포구 양화로 123",
                "101동 202호",
                "1144012300",
                new BigDecimal("37.5563"),
                new BigDecimal("126.9220")
        );
    }
}