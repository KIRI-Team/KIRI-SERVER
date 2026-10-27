package com.kiri.server.domain.bucket.service;

import com.kiri.server.domain.bucket.dto.BucketResDTO;
import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.bucket.enums.BucketStatus;
import com.kiri.server.domain.bucket.exception.BucketException;
import com.kiri.server.domain.bucket.exception.code.BucketErrorCode;
import com.kiri.server.domain.bucket.repository.BucketItemRepository;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.mission.exception.MissionException;
import com.kiri.server.domain.mission.exception.code.MissionErrorCode;
import com.kiri.server.domain.mission.repository.MissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class BucketServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long MISSION_ID = 10L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private BucketItemRepository bucketItemRepository;

    private BucketService bucketService;

    @BeforeEach
    void setUp() {
        bucketService = new BucketService(memberRepository, missionRepository, bucketItemRepository, CLOCK);
    }

    @Test
    @DisplayName("저장하면 SAVED 상태로 회원의 가족과 현재 시각을 담아 저장하고 결과를 반환한다.")
    void saveBucket_success() {
        Family family = family();
        Member member = member(family);
        Mission mission = mission(MissionStatus.ACTIVE, null);
        givenSavable(member, mission);
        given(bucketItemRepository.save(any(BucketItem.class))).willAnswer(invocation -> persisted(invocation.getArgument(0)));

        BucketResDTO.Saved saved = bucketService.saveBucket(MEMBER_ID, MISSION_ID);

        ArgumentCaptor<BucketItem> captor = ArgumentCaptor.forClass(BucketItem.class);
        verify(bucketItemRepository).save(captor.capture());
        BucketItem item = captor.getValue();
        assertThat(item.getFamily()).isSameAs(family);
        assertThat(item.getMember()).isSameAs(member);
        assertThat(item.getMission()).isSameAs(mission);
        assertThat(item.getStatus()).isEqualTo(BucketStatus.SAVED);
        assertThat(item.getSavedAt()).isEqualTo(LocalDateTime.of(2026, 10, 27, 19, 0));
        assertThat(saved).extracting(BucketResDTO.Saved::bucketItemId, BucketResDTO.Saved::missionId, BucketResDTO.Saved::status)
                .containsExactly(15L, MISSION_ID, BucketStatus.SAVED);
    }

    @Test
    @DisplayName("종료일이 오늘인 미션과 종료일이 없는 미션은 저장할 수 있다.")
    void saveBucket_endDateBoundary() {
        Member member = member(family());
        given(memberRepository.findByIdForUpdate(MEMBER_ID)).willReturn(Optional.of(member));
        given(bucketItemRepository.save(any(BucketItem.class))).willAnswer(invocation -> persisted(invocation.getArgument(0)));

        given(missionRepository.findById(MISSION_ID)).willReturn(Optional.of(mission(MissionStatus.ACTIVE, TODAY)));
        assertThat(bucketService.saveBucket(MEMBER_ID, MISSION_ID).status()).isEqualTo(BucketStatus.SAVED);

        given(missionRepository.findById(MISSION_ID)).willReturn(Optional.of(mission(MissionStatus.ACTIVE, null)));
        assertThat(bucketService.saveBucket(MEMBER_ID, MISSION_ID).status()).isEqualTo(BucketStatus.SAVED);
    }

    @Test
    @DisplayName("완료된 항목만 있으면 SAVED·SCHEDULED 중복이 아니므로 다시 저장할 수 있다.")
    void saveBucket_afterCompleted() {
        givenSavable(member(family()), mission(MissionStatus.ACTIVE, null));
        given(bucketItemRepository.existsByMemberIdAndMissionIdAndStatusIn(MEMBER_ID, MISSION_ID, BucketStatus.ACTIVE_STATUSES))
                .willReturn(false);
        given(bucketItemRepository.save(any(BucketItem.class))).willAnswer(invocation -> persisted(invocation.getArgument(0)));

        assertThat(bucketService.saveBucket(MEMBER_ID, MISSION_ID).status()).isEqualTo(BucketStatus.SAVED);
    }

    @Test
    @DisplayName("이미 SAVED·SCHEDULED 상태로 저장했다면 BUCKET409_1을 던지고 저장하지 않는다.")
    void saveBucket_alreadySaved() {
        givenSavable(member(family()), mission(MissionStatus.ACTIVE, null));
        given(bucketItemRepository.existsByMemberIdAndMissionIdAndStatusIn(MEMBER_ID, MISSION_ID, BucketStatus.ACTIVE_STATUSES))
                .willReturn(true);

        assertThatThrownBy(() -> bucketService.saveBucket(MEMBER_ID, MISSION_ID))
                .isInstanceOf(BucketException.class)
                .extracting("errorCode").isEqualTo(BucketErrorCode.ALREADY_SAVED);
        verify(bucketItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("가족이 없는 회원은 BUCKET403_1을 던지고 미션을 조회하지 않는다.")
    void saveBucket_noFamily() {
        given(memberRepository.findByIdForUpdate(MEMBER_ID)).willReturn(Optional.of(member(null)));

        assertThatThrownBy(() -> bucketService.saveBucket(MEMBER_ID, MISSION_ID))
                .isInstanceOf(BucketException.class)
                .extracting("errorCode").isEqualTo(BucketErrorCode.FAMILY_REQUIRED);
        verifyNoInteractions(missionRepository, bucketItemRepository);
    }

    @Test
    @DisplayName("토큰의 회원이 없으면 MEMBER404_1을 던진다.")
    void saveBucket_memberNotFound() {
        given(memberRepository.findByIdForUpdate(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bucketService.saveBucket(MEMBER_ID, MISSION_ID))
                .isInstanceOf(MemberException.class)
                .extracting("errorCode").isEqualTo(MemberErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("없는 미션이거나 숨김·검수 대기·취소 미션이면 MISSION404_1을 던진다.")
    void saveBucket_missionNotVisible() {
        given(memberRepository.findByIdForUpdate(MEMBER_ID)).willReturn(Optional.of(member(family())));
        given(missionRepository.findById(MISSION_ID)).willReturn(Optional.empty());
        assertNotFound();

        for (MissionStatus status : new MissionStatus[]{MissionStatus.HIDDEN, MissionStatus.CHECK_REQUIRED, MissionStatus.CANCELLED}) {
            given(missionRepository.findById(MISSION_ID)).willReturn(Optional.of(mission(status, null)));
            assertNotFound();
        }
        verifyNoInteractions(bucketItemRepository);
    }

    @Test
    @DisplayName("종료된 미션이나 종료일이 지난 ACTIVE 미션은 BUCKET409_2를 던진다.")
    void saveBucket_notSavable() {
        given(memberRepository.findByIdForUpdate(MEMBER_ID)).willReturn(Optional.of(member(family())));

        given(missionRepository.findById(MISSION_ID)).willReturn(Optional.of(mission(MissionStatus.ENDED, TODAY.minusDays(1))));
        assertNotSavable();

        given(missionRepository.findById(MISSION_ID)).willReturn(Optional.of(mission(MissionStatus.ACTIVE, TODAY.minusDays(1))));
        assertNotSavable();
        verifyNoInteractions(bucketItemRepository);
    }

    private void assertNotFound() {
        assertThatThrownBy(() -> bucketService.saveBucket(MEMBER_ID, MISSION_ID))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode").isEqualTo(MissionErrorCode.NOT_FOUND);
    }

    private void assertNotSavable() {
        assertThatThrownBy(() -> bucketService.saveBucket(MEMBER_ID, MISSION_ID))
                .isInstanceOf(BucketException.class)
                .extracting("errorCode").isEqualTo(BucketErrorCode.MISSION_NOT_SAVABLE);
    }

    private void givenSavable(Member member, Mission mission) {
        given(memberRepository.findByIdForUpdate(MEMBER_ID)).willReturn(Optional.of(member));
        given(missionRepository.findById(MISSION_ID)).willReturn(Optional.of(mission));
    }

    private BucketItem persisted(BucketItem item) {
        return BucketItem.builder()
                .id(15L)
                .family(item.getFamily())
                .member(item.getMember())
                .mission(item.getMission())
                .status(item.getStatus())
                .savedAt(item.getSavedAt())
                .build();
    }

    private Family family() {
        return Family.builder().id(1L).name("끼리네").build();
    }

    private Member member(Family family) {
        return Member.builder().id(MEMBER_ID).family(family).build();
    }

    private Mission mission(MissionStatus status, LocalDate endDate) {
        return Mission.builder()
                .id(MISSION_ID)
                .title("서울숲 방문하기")
                .description("설명")
                .category(Category.OUTDOOR)
                .missionType(endDate == null ? MissionType.PERMANENT : MissionType.PERIOD)
                .status(status)
                .endDate(endDate)
                .build();
    }
}
