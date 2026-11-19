package com.kiri.server.domain.taste.service;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.taste.dto.TasteReqDTO;
import com.kiri.server.domain.taste.dto.TasteResDTO;
import com.kiri.server.domain.taste.entity.MemberTaste;
import com.kiri.server.domain.taste.enums.ActivityLevel;
import com.kiri.server.domain.taste.enums.DurationLevel;
import com.kiri.server.domain.taste.enums.PriceLevel;
import com.kiri.server.domain.taste.event.MemberTasteUpdatedEvent;
import com.kiri.server.domain.taste.exception.TasteException;
import com.kiri.server.domain.taste.exception.code.TasteErrorCode;
import com.kiri.server.domain.taste.repository.MemberTasteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TasteServiceTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberTasteRepository memberTasteRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TasteService tasteService;

    @Test
    @DisplayName("저장된 취향과 닉네임을 조회한다.")
    void getMyTaste() {
        Member member = member(null);
        MemberTaste taste = taste(member, List.of(Category.FOOD_CAFE, Category.EXHIBITION));
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(memberTasteRepository.findWithCategoriesByMemberId(MEMBER_ID)).thenReturn(Optional.of(taste));

        TasteResDTO.MyTaste result = tasteService.getMyTaste(MEMBER_ID);

        assertThat(result.nickname()).isEqualTo("쥬123");
        assertThat(result.isSet()).isTrue();
        assertThat(result.categories()).containsExactly(Category.EXHIBITION, Category.FOOD_CAFE);
        assertThat(result.indoorOutdoor()).isEqualTo(IndoorOutdoor.INDOOR);
        assertThat(result.activityLevel()).isEqualTo(ActivityLevel.LIGHT);
        assertThat(result.priceLevel()).isEqualTo(PriceLevel.FREE);
        assertThat(result.durationLevel()).isEqualTo(DurationLevel.WITHIN_1H);
    }

    @Test
    @DisplayName("취향을 설정하지 않았으면 isSet=false와 빈 값을 돌려준다.")
    void getMyTasteNotSet() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member(null)));
        when(memberTasteRepository.findWithCategoriesByMemberId(MEMBER_ID)).thenReturn(Optional.empty());

        TasteResDTO.MyTaste result = tasteService.getMyTaste(MEMBER_ID);

        assertThat(result.nickname()).isEqualTo("쥬123");
        assertThat(result.isSet()).isFalse();
        assertThat(result.categories()).isEmpty();
        assertThat(result.indoorOutdoor()).isNull();
        assertThat(result.activityLevel()).isNull();
        assertThat(result.priceLevel()).isNull();
        assertThat(result.durationLevel()).isNull();
    }

    @Test
    @DisplayName("회원이 없으면 조회에 실패한다.")
    void getMyTasteMemberNotFound() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tasteService.getMyTaste(MEMBER_ID))
                .isInstanceOf(MemberException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("처음 저장하면 취향을 새로 만들고 변경 이벤트를 발행한다.")
    void saveNew() {
        Family family = mock(Family.class);
        when(family.getId()).thenReturn(10L);
        Member member = member(family);
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(memberTasteRepository.findWithCategoriesByMemberId(MEMBER_ID)).thenReturn(Optional.empty());

        TasteResDTO.MyTaste result = tasteService.saveMyTaste(MEMBER_ID, request(List.of(Category.POPUP), IndoorOutdoor.OUTDOOR));

        ArgumentCaptor<MemberTaste> saved = ArgumentCaptor.forClass(MemberTaste.class);
        verify(memberTasteRepository).save(saved.capture());
        assertThat(saved.getValue().getMember()).isEqualTo(member);
        assertThat(saved.getValue().getCategoryValues()).containsExactly(Category.POPUP);
        assertThat(result.isSet()).isTrue();
        assertThat(result.indoorOutdoor()).isEqualTo(IndoorOutdoor.OUTDOOR);
        verify(eventPublisher).publishEvent(new MemberTasteUpdatedEvent(MEMBER_ID, 10L));
    }

    @Test
    @DisplayName("이미 있으면 요청 값으로 덮어쓰고, 가족이 없으면 familyId 없이 이벤트를 발행한다.")
    void saveExisting() {
        Member member = member(null);
        MemberTaste taste = taste(member, List.of(Category.EXHIBITION, Category.FOOD_CAFE));
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(memberTasteRepository.findWithCategoriesByMemberId(MEMBER_ID)).thenReturn(Optional.of(taste));

        TasteResDTO.MyTaste result = tasteService.saveMyTaste(MEMBER_ID,
                new TasteReqDTO.Save(List.of(Category.FOOD_CAFE, Category.OUTDOOR), null, ActivityLevel.ACTIVE, null, null));

        assertThat(result.categories()).containsExactly(Category.FOOD_CAFE, Category.OUTDOOR);
        assertThat(result.indoorOutdoor()).isNull();
        assertThat(result.activityLevel()).isEqualTo(ActivityLevel.ACTIVE);
        assertThat(result.priceLevel()).isNull();
        assertThat(result.durationLevel()).isNull();
        assertThat(taste.getCategoryValues()).containsExactly(Category.FOOD_CAFE, Category.OUTDOOR);
        verify(eventPublisher).publishEvent(new MemberTasteUpdatedEvent(MEMBER_ID, null));
    }

    @Test
    @DisplayName("공간에 BOTH가 오면 아무것도 저장하지 않고 실패한다.")
    void saveBoth() {
        assertThatThrownBy(() -> tasteService.saveMyTaste(MEMBER_ID, request(List.of(Category.POPUP), IndoorOutdoor.BOTH)))
                .isInstanceOf(TasteException.class)
                .extracting("errorCode")
                .isEqualTo(TasteErrorCode.INVALID_INDOOR_OUTDOOR);

        verifyNoInteractions(memberRepository, memberTasteRepository, eventPublisher);
    }

    @Test
    @DisplayName("선호 활동이 중복되면 아무것도 저장하지 않고 실패한다.")
    void saveDuplicateCategory() {
        assertThatThrownBy(() -> tasteService.saveMyTaste(MEMBER_ID,
                request(List.of(Category.POPUP, Category.POPUP), IndoorOutdoor.INDOOR)))
                .isInstanceOf(TasteException.class)
                .extracting("errorCode")
                .isEqualTo(TasteErrorCode.DUPLICATE_CATEGORY);

        verifyNoInteractions(memberRepository, memberTasteRepository, eventPublisher);
    }

    @Test
    @DisplayName("회원이 없으면 저장에 실패하고 이벤트를 발행하지 않는다.")
    void saveMemberNotFound() {
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tasteService.saveMyTaste(MEMBER_ID, request(List.of(Category.POPUP), null)))
                .isInstanceOf(MemberException.class);

        verify(memberTasteRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("탈퇴 시 저장된 취향을 삭제하고, 없으면 아무것도 하지 않는다.")
    void deleteTaste() {
        MemberTaste taste = taste(member(null), List.of(Category.POPUP));
        when(memberTasteRepository.findWithCategoriesByMemberId(MEMBER_ID)).thenReturn(Optional.of(taste));
        when(memberTasteRepository.findWithCategoriesByMemberId(2L)).thenReturn(Optional.empty());

        tasteService.deleteTaste(MEMBER_ID);
        tasteService.deleteTaste(2L);

        verify(memberTasteRepository, times(1)).delete(taste);
    }

    private Member member(Family family) {
        return Member.builder()
                .id(MEMBER_ID)
                .nickname("쥬123")
                .family(family)
                .build();
    }

    private MemberTaste taste(Member member, List<Category> categories) {
        MemberTaste taste = MemberTaste.builder().member(member).build();
        taste.update(IndoorOutdoor.INDOOR, ActivityLevel.LIGHT, PriceLevel.FREE, DurationLevel.WITHIN_1H, categories);
        return taste;
    }

    private TasteReqDTO.Save request(List<Category> categories, IndoorOutdoor indoorOutdoor) {
        return new TasteReqDTO.Save(categories, indoorOutdoor, ActivityLevel.LIGHT, PriceLevel.FREE, DurationLevel.WITHIN_1H);
    }
}
