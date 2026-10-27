package com.kiri.server.domain.taste.service;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.taste.dto.TasteProfileDTO;
import com.kiri.server.domain.taste.entity.MemberTaste;
import com.kiri.server.domain.taste.enums.ActivityLevel;
import com.kiri.server.domain.taste.enums.DurationLevel;
import com.kiri.server.domain.taste.enums.PriceLevel;
import com.kiri.server.domain.taste.repository.MemberTasteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TasteProfileServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberTasteRepository memberTasteRepository;

    @InjectMocks
    private TasteProfileService tasteProfileService;

    @Test
    @DisplayName("회원 취향을 프로필로 바꾸고 활동시간을 분 범위로 함께 준다.")
    void memberProfile() {
        when(memberRepository.existsById(1L)).thenReturn(true);
        when(memberTasteRepository.findWithCategoriesByMemberId(1L))
                .thenReturn(Optional.of(taste(1L, DurationLevel.H1_TO_3, Category.FOOD_CAFE, Category.EXHIBITION)));

        TasteProfileDTO.MemberProfile profile = tasteProfileService.getMemberProfile(1L);

        assertThat(profile.memberId()).isEqualTo(1L);
        assertThat(profile.isSet()).isTrue();
        assertThat(profile.categories()).containsExactly(Category.EXHIBITION, Category.FOOD_CAFE);
        assertThat(profile.indoorOutdoor()).isEqualTo(IndoorOutdoor.INDOOR);
        assertThat(profile.activityLevel()).isEqualTo(ActivityLevel.LIGHT);
        assertThat(profile.priceLevel()).isEqualTo(PriceLevel.FREE);
        assertThat(profile.durationLevel()).isEqualTo(DurationLevel.H1_TO_3);
        assertThat(profile.durationMinMinutes()).isEqualTo(60);
        assertThat(profile.durationMaxMinutes()).isEqualTo(180);
    }

    @Test
    @DisplayName("반나절 이상은 상한이 없고, 상관없음이면 분 범위도 비운다.")
    void durationRange() {
        when(memberRepository.existsById(1L)).thenReturn(true);
        when(memberRepository.existsById(2L)).thenReturn(true);
        when(memberTasteRepository.findWithCategoriesByMemberId(1L))
                .thenReturn(Optional.of(taste(1L, DurationLevel.HALF_DAY_PLUS, Category.OUTDOOR)));
        when(memberTasteRepository.findWithCategoriesByMemberId(2L))
                .thenReturn(Optional.of(taste(2L, null, Category.OUTDOOR)));

        TasteProfileDTO.MemberProfile halfDay = tasteProfileService.getMemberProfile(1L);
        TasteProfileDTO.MemberProfile any = tasteProfileService.getMemberProfile(2L);

        assertThat(halfDay.durationMinMinutes()).isEqualTo(180);
        assertThat(halfDay.durationMaxMinutes()).isNull();
        assertThat(any.isSet()).isTrue();
        assertThat(any.durationMinMinutes()).isNull();
        assertThat(any.durationMaxMinutes()).isNull();
    }

    @Test
    @DisplayName("취향을 설정하지 않은 회원은 isSet=false 프로필을 준다.")
    void memberProfileNotSet() {
        when(memberRepository.existsById(1L)).thenReturn(true);
        when(memberTasteRepository.findWithCategoriesByMemberId(1L)).thenReturn(Optional.empty());

        TasteProfileDTO.MemberProfile profile = tasteProfileService.getMemberProfile(1L);

        assertThat(profile.isSet()).isFalse();
        assertThat(profile.categories()).isEmpty();
        assertThat(profile.activityLevel()).isNull();
    }

    @Test
    @DisplayName("회원이 없으면 프로필 조회에 실패한다.")
    void memberProfileNotFound() {
        when(memberRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> tasteProfileService.getMemberProfile(1L))
                .isInstanceOf(MemberException.class);
    }

    @Test
    @DisplayName("가족 구성원 순서대로 프로필을 묶고, 설정하지 않은 구성원도 포함한다.")
    void familyProfile() {
        when(memberTasteRepository.findMemberIdsByFamilyId(10L)).thenReturn(List.of(1L, 2L, 3L));
        when(memberTasteRepository.findAllWithCategoriesByMemberIdIn(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(taste(3L, null, Category.OUTDOOR), taste(1L, DurationLevel.WITHIN_1H, Category.EXHIBITION)));

        TasteProfileDTO.FamilyProfile profile = tasteProfileService.getFamilyProfile(10L);

        assertThat(profile.familyId()).isEqualTo(10L);
        assertThat(profile.members())
                .extracting(TasteProfileDTO.MemberProfile::memberId, TasteProfileDTO.MemberProfile::isSet)
                .containsExactly(
                        tuple(1L, true),
                        tuple(2L, false),
                        tuple(3L, true));
        assertThat(profile.setMemberCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("구성원이 없는 가족은 빈 묶음을 주고 취향을 조회하지 않는다.")
    void emptyFamily() {
        when(memberTasteRepository.findMemberIdsByFamilyId(10L)).thenReturn(List.of());

        TasteProfileDTO.FamilyProfile profile = tasteProfileService.getFamilyProfile(10L);

        assertThat(profile.members()).isEmpty();
        assertThat(profile.setMemberCount()).isZero();
        verify(memberTasteRepository, never()).findAllWithCategoriesByMemberIdIn(anyCollection());
    }

    private MemberTaste taste(Long memberId, DurationLevel durationLevel, Category... categories) {
        Member member = Member.builder().id(memberId).build();
        MemberTaste taste = MemberTaste.builder().member(member).build();
        taste.update(IndoorOutdoor.INDOOR, ActivityLevel.LIGHT, PriceLevel.FREE, durationLevel, List.of(categories));
        return taste;
    }
}
