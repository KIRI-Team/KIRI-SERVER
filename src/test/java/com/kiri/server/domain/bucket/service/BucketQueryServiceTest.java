package com.kiri.server.domain.bucket.service;

import com.kiri.server.domain.bucket.dto.BucketReqDTO;
import com.kiri.server.domain.bucket.dto.BucketResDTO;
import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.bucket.enums.BucketStatus;
import com.kiri.server.domain.bucket.exception.BucketException;
import com.kiri.server.domain.bucket.exception.code.BucketErrorCode;
import com.kiri.server.domain.bucket.repository.BucketItemRepository;
import com.kiri.server.domain.bucket.repository.BucketItemRepository.SaverBucketCount;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.enums.FamilyRole;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.enums.MissionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class BucketQueryServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long FAMILY_ID = 7L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final LocalDateTime SAVED_AT = LocalDateTime.of(2026, 10, 27, 19, 40);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private BucketItemRepository bucketItemRepository;

    private BucketQueryService bucketQueryService;

    private Family family;
    private Member mom;
    private Member dad;

    @BeforeEach
    void setUp() {
        bucketQueryService = new BucketQueryService(memberRepository, bucketItemRepository, CLOCK);
        family = Family.builder().id(FAMILY_ID).name("끼리네").build();
        mom = member(MEMBER_ID, "끼리엄마", FamilyRole.MOTHER);
        dad = member(2L, "끼리아빠", FamilyRole.FATHER);
    }

    @Test
    @DisplayName("한 건 더 조회해 다음 페이지 여부를 판단하고, 이번 페이지 마지막 항목 ID를 nextCursor로 반환한다.")
    void getFamilyBuckets_hasNext() {
        givenFamily(List.of(mom, dad), List.of());
        given(bucketItemRepository.findByFamilyIdAndIdLessThanOrderByIdDesc(FAMILY_ID, Long.MAX_VALUE, PageRequest.of(0, 3)))
                .willReturn(List.of(item(30L, dad, BucketStatus.SAVED, mission(MissionStatus.ACTIVE, null)),
                        item(20L, mom, BucketStatus.SAVED, mission(MissionStatus.ACTIVE, null)),
                        item(10L, dad, BucketStatus.SAVED, mission(MissionStatus.ACTIVE, null))));

        BucketResDTO.FamilyBuckets result = bucketQueryService.getFamilyBuckets(MEMBER_ID, condition(null, null, 2));

        assertThat(result.bucketItems()).extracting(BucketResDTO.FamilyBucketItem::bucketItemId).containsExactly(30L, 20L);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo(20L);
    }

    @Test
    @DisplayName("마지막 페이지면 nextCursor는 null이고 hasNext는 false다. 개수를 생략하면 20건 기준으로 조회한다.")
    void getFamilyBuckets_lastPage() {
        givenFamily(List.of(mom, dad), List.of());
        given(bucketItemRepository.findByFamilyIdAndIdLessThanOrderByIdDesc(FAMILY_ID, 30L, PageRequest.of(0, 21)))
                .willReturn(List.of(item(10L, dad, BucketStatus.SAVED, mission(MissionStatus.ACTIVE, null))));

        BucketResDTO.FamilyBuckets result = bucketQueryService.getFamilyBuckets(MEMBER_ID, condition(null, 30L, null));

        assertThat(result.bucketItems()).hasSize(1);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("저장자를 지정하면 그 구성원의 항목만 조회한다.")
    void getFamilyBuckets_memberFilter() {
        givenFamily(List.of(mom, dad), List.of());
        given(bucketItemRepository.findByFamilyIdAndMemberIdAndIdLessThanOrderByIdDesc(
                FAMILY_ID, 2L, Long.MAX_VALUE, PageRequest.of(0, 21)))
                .willReturn(List.of(item(30L, dad, BucketStatus.SAVED, mission(MissionStatus.ACTIVE, null))));

        BucketResDTO.FamilyBuckets result = bucketQueryService.getFamilyBuckets(MEMBER_ID, condition(2L, null, null));

        assertThat(result.bucketItems()).extracting(card -> card.saver().memberId()).containsExactly(2L);
    }

    @Test
    @DisplayName("필터 칩용 저장자는 탈퇴하지 않은 가족 구성원을 ID 순으로 담고, 저장하지 않은 구성원은 0개로 담는다.")
    void getFamilyBuckets_savers() {
        Member daughter = member(3L, "끼리딸", FamilyRole.DAUGHTER);
        givenFamily(List.of(dad, daughter, mom), List.of(count(2L, 2L), count(MEMBER_ID, 1L), count(4L, 3L)));
        given(bucketItemRepository.findByFamilyIdAndIdLessThanOrderByIdDesc(FAMILY_ID, Long.MAX_VALUE, PageRequest.of(0, 21)))
                .willReturn(List.of());

        BucketResDTO.FamilyBuckets result = bucketQueryService.getFamilyBuckets(MEMBER_ID, condition(null, null, null));

        assertThat(result.savers())
                .extracting(BucketResDTO.SaverCount::memberId, BucketResDTO.SaverCount::nickname,
                        BucketResDTO.SaverCount::familyRole, BucketResDTO.SaverCount::bucketCount)
                .containsExactly(
                        tuple(MEMBER_ID, "끼리엄마", FamilyRole.MOTHER, 1L),
                        tuple(2L, "끼리아빠", FamilyRole.FATHER, 2L),
                        tuple(3L, "끼리딸", FamilyRole.DAUGHTER, 0L));
        assertThat(result.bucketItems()).isEmpty();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("카드에 미션 요약과 저장자를 담고, 일정 정보는 null로 내려준다. 이미지가 없으면 출처도 null이다.")
    void getFamilyBuckets_card() {
        givenFamily(List.of(mom, dad), List.of());
        Mission noImage = Mission.builder()
                .id(98L)
                .title("한강 뚜벅뚜벅 축제 방문하기")
                .description("설명")
                .category(Category.OUTDOOR)
                .missionType(MissionType.PERIOD)
                .status(MissionStatus.ACTIVE)
                .endDate(TODAY.plusDays(3))
                .imageSource("한국관광공사 (공공누리 제1유형)")
                .build();
        given(bucketItemRepository.findByFamilyIdAndIdLessThanOrderByIdDesc(FAMILY_ID, Long.MAX_VALUE, PageRequest.of(0, 21)))
                .willReturn(List.of(item(42L, dad, BucketStatus.SAVED, mission(MissionStatus.ACTIVE, null)),
                        item(31L, mom, BucketStatus.SAVED, noImage)));

        BucketResDTO.FamilyBuckets result = bucketQueryService.getFamilyBuckets(MEMBER_ID, condition(null, null, null));

        BucketResDTO.FamilyBucketItem card = result.bucketItems().getFirst();
        assertThat(card.bucketItemId()).isEqualTo(42L);
        assertThat(card.status()).isEqualTo(BucketStatus.SAVED);
        assertThat(card.savedAt()).isEqualTo(SAVED_AT);
        assertThat(card.completedAt()).isNull();
        assertThat(card.mission()).extracting(BucketResDTO.MissionSummary::missionId, BucketResDTO.MissionSummary::title,
                        BucketResDTO.MissionSummary::imageSource, BucketResDTO.MissionSummary::status)
                .containsExactly(130L, "광장시장에서 시장음식 먹어보기", "한국관광공사 (공공누리 제1유형)", MissionStatus.ACTIVE);
        assertThat(card.saver()).extracting(BucketResDTO.Saver::memberId, BucketResDTO.Saver::nickname,
                        BucketResDTO.Saver::familyRole, BucketResDTO.Saver::withdrawn)
                .containsExactly(2L, "끼리아빠", FamilyRole.FATHER, false);
        assertThat(card.scheduleId()).isNull();
        assertThat(card.scheduledDate()).isNull();

        BucketResDTO.MissionSummary noImageSummary = result.bucketItems().getLast().mission();
        assertThat(noImageSummary.imageUrl()).isNull();
        assertThat(noImageSummary.imageSource()).isNull();
    }

    @Test
    @DisplayName("저장한 회원이 탈퇴했으면 탈퇴로 표시하고 닉네임은 '탈퇴한 사용자', 역할은 null로 내려준다.")
    void getFamilyBuckets_withdrawnSaver() {
        Member withdrawn = Member.builder()
                .id(4L)
                .family(family)
                .deletedAt(SAVED_AT)
                .build();
        givenFamily(List.of(mom, dad), List.of());
        given(bucketItemRepository.findByFamilyIdAndIdLessThanOrderByIdDesc(FAMILY_ID, Long.MAX_VALUE, PageRequest.of(0, 21)))
                .willReturn(List.of(item(31L, withdrawn, BucketStatus.COMPLETED, mission(MissionStatus.ACTIVE, null))));

        BucketResDTO.FamilyBuckets result = bucketQueryService.getFamilyBuckets(MEMBER_ID, condition(null, null, null));

        assertThat(result.bucketItems().getFirst().saver())
                .extracting(BucketResDTO.Saver::memberId, BucketResDTO.Saver::nickname,
                        BucketResDTO.Saver::familyRole, BucketResDTO.Saver::withdrawn)
                .containsExactly(4L, "탈퇴한 사용자", null, true);
    }

    @Test
    @DisplayName("저장자 회원이 삭제돼 비어 있으면 탈퇴로 표시하고 저장자 정보는 null로 내려준다.")
    void getFamilyBuckets_deletedSaver() {
        givenFamily(List.of(mom, dad), List.of());
        given(bucketItemRepository.findByFamilyIdAndIdLessThanOrderByIdDesc(FAMILY_ID, Long.MAX_VALUE, PageRequest.of(0, 21)))
                .willReturn(List.of(item(31L, null, BucketStatus.SAVED, mission(MissionStatus.ACTIVE, null))));

        BucketResDTO.FamilyBuckets result = bucketQueryService.getFamilyBuckets(MEMBER_ID, condition(null, null, null));

        assertThat(result.bucketItems().getFirst().saver())
                .extracting(BucketResDTO.Saver::memberId, BucketResDTO.Saver::nickname,
                        BucketResDTO.Saver::familyRole, BucketResDTO.Saver::withdrawn)
                .containsExactly(null, null, null, true);
    }

    @Test
    @DisplayName("SAVED이면서 공개 중이고 종료일이 지나지 않은 미션만 일정을 잡을 수 있다.")
    void getFamilyBuckets_schedulable() {
        givenFamily(List.of(mom, dad), List.of());
        given(bucketItemRepository.findByFamilyIdAndIdLessThanOrderByIdDesc(FAMILY_ID, Long.MAX_VALUE, PageRequest.of(0, 21)))
                .willReturn(List.of(
                        item(60L, dad, BucketStatus.SAVED, mission(MissionStatus.ACTIVE, null)),
                        item(50L, dad, BucketStatus.SAVED, mission(MissionStatus.ACTIVE, TODAY)),
                        item(40L, dad, BucketStatus.SAVED, mission(MissionStatus.ACTIVE, TODAY.minusDays(1))),
                        item(30L, dad, BucketStatus.SAVED, mission(MissionStatus.ENDED, TODAY.minusDays(1))),
                        item(20L, dad, BucketStatus.SCHEDULED, mission(MissionStatus.ACTIVE, null)),
                        item(10L, dad, BucketStatus.COMPLETED, mission(MissionStatus.ACTIVE, null))));

        BucketResDTO.FamilyBuckets result = bucketQueryService.getFamilyBuckets(MEMBER_ID, condition(null, null, null));

        assertThat(result.bucketItems()).extracting(BucketResDTO.FamilyBucketItem::schedulable)
                .containsExactly(true, true, false, false, false, false);
    }

    @Test
    @DisplayName("가족이 없는 회원은 BUCKET403_1을 던지고 버킷 항목을 조회하지 않는다.")
    void getFamilyBuckets_noFamily() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().id(MEMBER_ID).build()));

        assertThatThrownBy(() -> bucketQueryService.getFamilyBuckets(MEMBER_ID, condition(null, null, null)))
                .isInstanceOf(BucketException.class)
                .extracting("errorCode").isEqualTo(BucketErrorCode.FAMILY_REQUIRED);
        verifyNoInteractions(bucketItemRepository);
    }

    @Test
    @DisplayName("토큰의 회원이 없으면 MEMBER404_1을 던진다.")
    void getFamilyBuckets_memberNotFound() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bucketQueryService.getFamilyBuckets(MEMBER_ID, condition(null, null, null)))
                .isInstanceOf(MemberException.class)
                .extracting("errorCode").isEqualTo(MemberErrorCode.NOT_FOUND);
    }

    private void givenFamily(List<Member> members, List<SaverBucketCount> counts) {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(mom));
        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(members);
        given(bucketItemRepository.findSaverCounts(FAMILY_ID)).willReturn(counts);
    }

    private BucketReqDTO.FamilyBucketCondition condition(Long memberId, Long cursor, Integer size) {
        return BucketReqDTO.FamilyBucketCondition.builder()
                .memberId(memberId)
                .cursor(cursor)
                .size(size)
                .build();
    }

    private Member member(Long id, String nickname, FamilyRole familyRole) {
        return Member.builder()
                .id(id)
                .family(family)
                .nickname(nickname)
                .familyRole(familyRole)
                .build();
    }

    private BucketItem item(Long id, Member member, BucketStatus status, Mission mission) {
        return BucketItem.builder()
                .id(id)
                .family(family)
                .member(member)
                .mission(mission)
                .status(status)
                .savedAt(SAVED_AT)
                .build();
    }

    private Mission mission(MissionStatus status, LocalDate endDate) {
        return Mission.builder()
                .id(130L)
                .title("광장시장에서 시장음식 먹어보기")
                .description("설명")
                .category(Category.FOOD_CAFE)
                .missionType(endDate == null ? MissionType.PERMANENT : MissionType.PERIOD)
                .status(status)
                .endDate(endDate)
                .imageUrl("https://tong.visitkorea.or.kr/cms/resource/00/0000000_image2_1.jpg")
                .imageSource("한국관광공사 (공공누리 제1유형)")
                .build();
    }

    private SaverBucketCount count(Long memberId, Long bucketCount) {
        return new SaverBucketCount() {
            @Override
            public Long getMemberId() {
                return memberId;
            }

            @Override
            public Long getBucketCount() {
                return bucketCount;
            }
        };
    }
}
