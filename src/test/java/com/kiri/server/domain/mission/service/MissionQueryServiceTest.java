package com.kiri.server.domain.mission.service;

import com.kiri.server.domain.bucket.enums.BucketStatus;
import com.kiri.server.domain.bucket.repository.BucketItemRepository;
import com.kiri.server.domain.external.entity.ExternalRecord;
import com.kiri.server.domain.external.repository.ExternalRecordRepository;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.dto.MissionReqDTO;
import com.kiri.server.domain.mission.dto.MissionResDTO;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.entity.MissionChecklistItem;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.ItemType;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.mission.exception.MissionException;
import com.kiri.server.domain.mission.exception.code.MissionErrorCode;
import com.kiri.server.domain.mission.repository.MissionChecklistItemRepository;
import com.kiri.server.domain.mission.repository.MissionFeedRepository;
import com.kiri.server.domain.mission.repository.MissionFeedRepository.BaseLocation;
import com.kiri.server.domain.mission.repository.MissionRepository;
import com.kiri.server.domain.place.entity.Place;
import com.kiri.server.domain.place.enums.PlaceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MissionQueryServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-18T15:30:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private MissionFeedRepository missionFeedRepository;

    @Mock
    private MissionChecklistItemRepository missionChecklistItemRepository;

    @Mock
    private ExternalRecordRepository externalRecordRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private BucketItemRepository bucketItemRepository;

    private MissionQueryService missionQueryService;

    @BeforeEach
    void setUp() {
        missionQueryService = new MissionQueryService(missionRepository, missionFeedRepository,
                missionChecklistItemRepository, externalRecordRepository, memberRepository, bucketItemRepository, CLOCK);
    }

    @Test
    @DisplayName("피드는 한 건 더 조회해 다음 페이지 여부를 판단하고, 조회한 순서대로 카드를 만든다.")
    void getFeed_hasNext() {
        MissionReqDTO.FeedCondition condition = MissionReqDTO.FeedCondition.builder().size(2).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member(null)));
        given(missionFeedRepository.findFeedIds(condition, null, TODAY, 3)).willReturn(List.of(30L, 20L, 10L));
        given(missionRepository.findAllWithPlaceByIdIn(List.of(30L, 20L)))
                .willReturn(List.of(mission(20L, MissionStatus.ACTIVE), mission(30L, MissionStatus.ACTIVE)));

        MissionResDTO.Feed feed = missionQueryService.getFeed(MEMBER_ID, condition);

        assertThat(feed.missions()).extracting(MissionResDTO.FeedItem::missionId).containsExactly(30L, 20L);
        assertThat(feed.hasNext()).isTrue();
        assertThat(feed.nextCursor()).isEqualTo(20L);
    }

    @Test
    @DisplayName("마지막 페이지면 nextCursor는 null이고 hasNext는 false다.")
    void getFeed_lastPage() {
        MissionReqDTO.FeedCondition condition = MissionReqDTO.FeedCondition.builder().build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member(null)));
        given(missionFeedRepository.findFeedIds(condition, null, TODAY, 21)).willReturn(List.of(10L));
        given(missionRepository.findAllWithPlaceByIdIn(List.of(10L))).willReturn(List.of(mission(10L, MissionStatus.ACTIVE)));

        MissionResDTO.Feed feed = missionQueryService.getFeed(MEMBER_ID, condition);

        assertThat(feed.missions()).hasSize(1);
        assertThat(feed.hasNext()).isFalse();
        assertThat(feed.nextCursor()).isNull();
    }

    @Test
    @DisplayName("조회 결과가 없으면 미션을 다시 조회하지 않고 빈 목록을 반환한다.")
    void getFeed_empty() {
        MissionReqDTO.FeedCondition condition = MissionReqDTO.FeedCondition.builder().build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member(null)));
        given(missionFeedRepository.findFeedIds(condition, null, TODAY, 21)).willReturn(List.of());

        MissionResDTO.Feed feed = missionQueryService.getFeed(MEMBER_ID, condition);

        assertThat(feed.missions()).isEmpty();
        assertThat(feed.hasNext()).isFalse();
        verify(missionRepository, never()).findAllWithPlaceByIdIn(any());
    }

    @Test
    @DisplayName("요청 좌표가 있으면 우리 집 위치 대신 요청 좌표 기준으로 거리(km)를 소수 첫째 자리까지 담는다.")
    void getFeed_distance() {
        MissionReqDTO.FeedCondition condition = MissionReqDTO.FeedCondition.builder()
                .latitude(37.5446).longitude(127.0557).build();
        given(missionFeedRepository.findFeedIds(condition, new BaseLocation(37.5446, 127.0557), TODAY, 21))
                .willReturn(List.of(10L));
        given(missionRepository.findAllWithPlaceByIdIn(List.of(10L))).willReturn(List.of(mission(10L, MissionStatus.ACTIVE)));

        MissionResDTO.Feed feed = missionQueryService.getFeed(MEMBER_ID, condition);

        assertThat(feed.missions().getFirst().distance()).isEqualTo(1.6);
        verifyNoInteractions(memberRepository);
    }

    @Test
    @DisplayName("요청 좌표가 없으면 가족의 우리 집 위치를 기준으로 거리를 계산한다.")
    void getFeed_homeLocation() {
        MissionReqDTO.FeedCondition condition = MissionReqDTO.FeedCondition.builder().distance(3).build();
        given(memberRepository.findById(MEMBER_ID))
                .willReturn(Optional.of(member(family(new BigDecimal("37.5446000"), new BigDecimal("127.0557000")))));
        given(missionFeedRepository.findFeedIds(condition, new BaseLocation(37.5446, 127.0557), TODAY, 21))
                .willReturn(List.of(10L));
        given(missionRepository.findAllWithPlaceByIdIn(List.of(10L))).willReturn(List.of(mission(10L, MissionStatus.ACTIVE)));

        MissionResDTO.Feed feed = missionQueryService.getFeed(MEMBER_ID, condition);

        assertThat(feed.missions().getFirst().distance()).isEqualTo(1.6);
    }

    @Test
    @DisplayName("우리 집 위치를 등록하지 않은 가족이면 기준 위치 없이 조회한다.")
    void getFeed_familyWithoutLocation() {
        MissionReqDTO.FeedCondition condition = MissionReqDTO.FeedCondition.builder().distance(3).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member(family(null, null))));
        given(missionFeedRepository.findFeedIds(condition, null, TODAY, 21)).willReturn(List.of(10L));
        given(missionRepository.findAllWithPlaceByIdIn(List.of(10L))).willReturn(List.of(mission(10L, MissionStatus.ACTIVE)));

        MissionResDTO.Feed feed = missionQueryService.getFeed(MEMBER_ID, condition);

        assertThat(feed.missions().getFirst().distance()).isNull();
    }

    @Test
    @DisplayName("토큰의 회원이 없으면 MEMBER404_1을 던진다.")
    void getFeed_memberNotFound() {
        MissionReqDTO.FeedCondition condition = MissionReqDTO.FeedCondition.builder().build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> missionQueryService.getFeed(MEMBER_ID, condition))
                .isInstanceOf(MemberException.class)
                .extracting("errorCode").isEqualTo(MemberErrorCode.NOT_FOUND);
        verifyNoInteractions(missionFeedRepository);
    }

    @Test
    @DisplayName("가족이 없는 회원(둘러보기)은 기준 위치 없이 조회하고 거리는 null이다.")
    void getFeed_noDistance() {
        MissionReqDTO.FeedCondition condition = MissionReqDTO.FeedCondition.builder().distance(3).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member(null)));
        given(missionFeedRepository.findFeedIds(condition, null, TODAY, 21)).willReturn(List.of(10L));
        given(missionRepository.findAllWithPlaceByIdIn(List.of(10L))).willReturn(List.of(mission(10L, MissionStatus.ACTIVE)));

        MissionResDTO.Feed feed = missionQueryService.getFeed(MEMBER_ID, condition);

        assertThat(feed.missions().getFirst().distance()).isNull();
    }

    @Test
    @DisplayName("거리가 1, 3, 5, 10km가 아니면 MISSION400_1을 던진다.")
    void getFeed_invalidDistance() {
        MissionReqDTO.FeedCondition condition = MissionReqDTO.FeedCondition.builder().distance(2).build();

        assertThatThrownBy(() -> missionQueryService.getFeed(MEMBER_ID, condition))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode").isEqualTo(MissionErrorCode.INVALID_DISTANCE);
        verifyNoInteractions(missionFeedRepository);
    }

    @Test
    @DisplayName("위도와 경도 중 하나만 있으면 MISSION400_2를 던진다.")
    void getFeed_coordinatePair() {
        MissionReqDTO.FeedCondition condition = MissionReqDTO.FeedCondition.builder().latitude(37.5).build();

        assertThatThrownBy(() -> missionQueryService.getFeed(MEMBER_ID, condition))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode").isEqualTo(MissionErrorCode.COORDINATE_PAIR_REQUIRED);
    }

    @Test
    @DisplayName("지역 코드가 숫자 2자리나 5자리가 아니면 MISSION400_3을 던진다.")
    void getFeed_invalidRegionCode() {
        MissionReqDTO.FeedCondition condition = MissionReqDTO.FeedCondition.builder().regionCode("112").build();

        assertThatThrownBy(() -> missionQueryService.getFeed(MEMBER_ID, condition))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode").isEqualTo(MissionErrorCode.INVALID_REGION_CODE);
    }

    @Test
    @DisplayName("상세는 미션 운영시간이 없으면 장소 운영시간을 쓰고, 준비물과 단계를 나눠 순서대로 담는다.")
    void getDetail_success() {
        Mission mission = mission(10L, MissionStatus.ACTIVE);
        given(missionRepository.findWithPlaceById(10L)).willReturn(Optional.of(mission));
        given(missionChecklistItemRepository.findAllByMissionOrderByItemTypeAscDisplayOrderAsc(mission)).willReturn(List.of(
                item(mission, ItemType.PREPARE, "사전 예약", 1),
                item(mission, ItemType.STEP, "기획 전시 관람하기", 2),
                item(mission, ItemType.STEP, "상설 전시 관람하기", 1)));
        ExternalRecord record = mock(ExternalRecord.class);
        given(record.getSourceUrl()).willReturn("https://korean.visitkorea.or.kr/detail");
        given(externalRecordRepository.findFirstByMissionAndSourceUrlIsNotNullOrderByIdAsc(mission))
                .willReturn(Optional.of(record));

        MissionResDTO.Detail detail = missionQueryService.getDetail(MEMBER_ID, 10L);

        assertThat(detail.openingTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(detail.closingTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(detail.prepareItems()).extracting(MissionResDTO.ChecklistItem::title).containsExactly("사전 예약");
        assertThat(detail.steps()).extracting(MissionResDTO.ChecklistItem::title)
                .containsExactly("상설 전시 관람하기", "기획 전시 관람하기");
        assertThat(detail.sourceUrl()).isEqualTo("https://korean.visitkorea.or.kr/detail");
        assertThat(detail.place().latitude()).isEqualTo(37.5444);
        assertThat(detail)
                .extracting(MissionResDTO.Detail::bucketSaved, MissionResDTO.Detail::averageRating, MissionResDTO.Detail::reviewCount)
                .containsExactly(false, null, 0);
    }

    @Test
    @DisplayName("미션 운영시간이 있으면 장소 운영시간 대신 미션 운영시간을 쓴다.")
    void getDetail_missionHours() {
        Mission mission = Mission.builder()
                .id(10L).place(place()).title("야간 개장 관람하기").description("설명")
                .category(Category.EXHIBITION).missionType(MissionType.PERIOD)
                .openingTime(LocalTime.of(19, 0)).closingTime(LocalTime.of(22, 0))
                .build();
        given(missionRepository.findWithPlaceById(10L)).willReturn(Optional.of(mission));
        given(missionChecklistItemRepository.findAllByMissionOrderByItemTypeAscDisplayOrderAsc(mission)).willReturn(List.of());
        given(externalRecordRepository.findFirstByMissionAndSourceUrlIsNotNullOrderByIdAsc(mission)).willReturn(Optional.empty());

        MissionResDTO.Detail detail = missionQueryService.getDetail(MEMBER_ID, 10L);

        assertThat(detail).extracting(MissionResDTO.Detail::openingTime, MissionResDTO.Detail::closingTime)
                .containsExactly(LocalTime.of(19, 0), LocalTime.of(22, 0));
        assertThat(detail.sourceUrl()).isNull();
    }

    @Test
    @DisplayName("이미지가 없으면 이미지 출처도 내려주지 않는다.")
    void getDetail_noImage() {
        Mission mission = Mission.builder()
                .id(10L).place(place()).title("서울숲 방문하기").description("설명")
                .category(Category.OUTDOOR).missionType(MissionType.PERMANENT)
                .imageSource("한국관광공사 (공공누리 제1유형)")
                .build();
        given(missionRepository.findWithPlaceById(10L)).willReturn(Optional.of(mission));
        given(missionChecklistItemRepository.findAllByMissionOrderByItemTypeAscDisplayOrderAsc(mission)).willReturn(List.of());
        given(externalRecordRepository.findFirstByMissionAndSourceUrlIsNotNullOrderByIdAsc(mission)).willReturn(Optional.empty());

        MissionResDTO.Detail detail = missionQueryService.getDetail(MEMBER_ID, 10L);

        assertThat(detail).extracting(MissionResDTO.Detail::imageUrl, MissionResDTO.Detail::imageSource)
                .containsExactly(null, null);
    }

    @Test
    @DisplayName("기간이 끝난 ENDED 미션도 상세를 조회할 수 있다.")
    void getDetail_ended() {
        Mission mission = mission(10L, MissionStatus.ENDED);
        given(missionRepository.findWithPlaceById(10L)).willReturn(Optional.of(mission));
        given(missionChecklistItemRepository.findAllByMissionOrderByItemTypeAscDisplayOrderAsc(mission)).willReturn(List.of());
        given(externalRecordRepository.findFirstByMissionAndSourceUrlIsNotNullOrderByIdAsc(mission)).willReturn(Optional.empty());

        assertThat(missionQueryService.getDetail(MEMBER_ID, 10L).status()).isEqualTo(MissionStatus.ENDED);
    }

    @Test
    @DisplayName("로그인한 회원이 SAVED·SCHEDULED 상태로 저장한 미션이면 bucketSaved가 true다.")
    void getDetail_bucketSaved() {
        Mission mission = mission(10L, MissionStatus.ACTIVE);
        given(missionRepository.findWithPlaceById(10L)).willReturn(Optional.of(mission));
        given(missionChecklistItemRepository.findAllByMissionOrderByItemTypeAscDisplayOrderAsc(mission)).willReturn(List.of());
        given(externalRecordRepository.findFirstByMissionAndSourceUrlIsNotNullOrderByIdAsc(mission)).willReturn(Optional.empty());
        given(bucketItemRepository.existsByMemberIdAndMissionIdAndStatusIn(MEMBER_ID, 10L, BucketStatus.ACTIVE_STATUSES))
                .willReturn(true);

        assertThat(missionQueryService.getDetail(MEMBER_ID, 10L).bucketSaved()).isTrue();
    }

    @Test
    @DisplayName("숨김·검수 대기·취소 미션은 MISSION404_1을 던진다.")
    void getDetail_notVisible() {
        given(missionRepository.findWithPlaceById(eq(1L))).willReturn(Optional.of(mission(1L, MissionStatus.HIDDEN)));
        given(missionRepository.findWithPlaceById(eq(2L))).willReturn(Optional.of(mission(2L, MissionStatus.CHECK_REQUIRED)));
        given(missionRepository.findWithPlaceById(eq(3L))).willReturn(Optional.of(mission(3L, MissionStatus.CANCELLED)));

        for (long id = 1; id <= 3; id++) {
            long missionId = id;
            assertThatThrownBy(() -> missionQueryService.getDetail(MEMBER_ID, missionId))
                    .isInstanceOf(MissionException.class)
                    .extracting("errorCode").isEqualTo(MissionErrorCode.NOT_FOUND);
        }
        verifyNoInteractions(missionChecklistItemRepository, bucketItemRepository);
    }

    @Test
    @DisplayName("없는 미션이면 MISSION404_1을 던진다.")
    void getDetail_notFound() {
        given(missionRepository.findWithPlaceById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> missionQueryService.getDetail(MEMBER_ID, 99L))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode").isEqualTo(MissionErrorCode.NOT_FOUND);
    }

    private Member member(Family family) {
        return Member.builder().id(MEMBER_ID).family(family).build();
    }

    private Family family(BigDecimal latitude, BigDecimal longitude) {
        return Family.builder().id(1L).name("끼리네").latitude(latitude).longitude(longitude).build();
    }

    private Place place() {
        return Place.builder()
                .id(1L)
                .name("서울숲")
                .placeType(PlaceType.TOURIST_SPOT)
                .latitude(new BigDecimal("37.5444000"))
                .longitude(new BigDecimal("127.0374000"))
                .address("서울특별시 성동구 뚝섬로 273")
                .openingTime(LocalTime.of(10, 0))
                .closingTime(LocalTime.of(18, 0))
                .build();
    }

    private Mission mission(Long id, MissionStatus status) {
        return Mission.builder()
                .id(id)
                .place(place())
                .title("서울숲 방문하기")
                .description("설명")
                .category(Category.OUTDOOR)
                .missionType(MissionType.PERMANENT)
                .status(status)
                .build();
    }

    private MissionChecklistItem item(Mission mission, ItemType itemType, String title, int order) {
        return MissionChecklistItem.builder()
                .mission(mission)
                .itemType(itemType)
                .title(title)
                .displayOrder(order)
                .build();
    }
}
