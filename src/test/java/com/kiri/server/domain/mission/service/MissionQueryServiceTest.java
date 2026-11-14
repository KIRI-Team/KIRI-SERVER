package com.kiri.server.domain.mission.service;

import com.kiri.server.domain.bucket.enums.BucketSaveState;
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
import com.kiri.server.domain.mission.enums.WalkingLevel;
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
import static org.mockito.ArgumentMatchers.anyDouble;
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

    @Mock
    private com.kiri.server.domain.pick.repository.KiriPickRepository kiriPickRepository;

    @Mock
    private com.kiri.server.domain.review.repository.ReviewRepository reviewRepository;

    private MissionQueryService missionQueryService;

    @BeforeEach
    void setUp() {
        missionQueryService = new MissionQueryService(missionRepository, missionFeedRepository,
                missionChecklistItemRepository, externalRecordRepository, memberRepository, bucketItemRepository,
                kiriPickRepository, reviewRepository, new NearbyMissionFinder(missionRepository), CLOCK);
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
    @DisplayName("상세는 미션 운영시간이 없으면 장소 운영시간을 쓰고, 단계를 순서대로 담는다.")
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
        given(reviewRepository.findMissionRating(10L)).willReturn(rating(4.3333, 3L));

        MissionResDTO.Detail detail = missionQueryService.getDetail(MEMBER_ID, 10L);

        assertThat(detail.openingTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(detail.closingTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(detail.steps()).extracting(MissionResDTO.ChecklistItem::title)
                .containsExactly("상설 전시 관람하기", "기획 전시 관람하기");
        assertThat(detail.sourceUrl()).isEqualTo("https://korean.visitkorea.or.kr/detail");
        assertThat(detail.walkingLevel()).isEqualTo(WalkingLevel.HIGH);
        assertThat(detail.place().latitude()).isEqualTo(37.5444);
        assertThat(detail)
                .extracting(MissionResDTO.Detail::bucketSaved, MissionResDTO.Detail::bucketState,
                        MissionResDTO.Detail::averageRating, MissionResDTO.Detail::reviewCount)
                .containsExactly(false, BucketSaveState.NONE, 4.3, 3);
    }

    @Test
    @DisplayName("별점을 남긴 전체 사용자 평균을 소수 첫째 자리로 내리고, 별점이 없으면 평균은 null·개수는 0이다.")
    void getDetail_rating() {
        Mission rated = mission(10L, MissionStatus.ACTIVE);
        given(missionRepository.findWithPlaceById(10L)).willReturn(Optional.of(rated));
        given(missionChecklistItemRepository.findAllByMissionOrderByItemTypeAscDisplayOrderAsc(rated)).willReturn(List.of());
        given(externalRecordRepository.findFirstByMissionAndSourceUrlIsNotNullOrderByIdAsc(rated)).willReturn(Optional.empty());
        given(reviewRepository.findMissionRating(10L)).willReturn(rating(3.75, 4L));

        MissionResDTO.Detail ratedDetail = missionQueryService.getDetail(MEMBER_ID, 10L);
        assertThat(ratedDetail).extracting(MissionResDTO.Detail::averageRating, MissionResDTO.Detail::reviewCount)
                .containsExactly(3.8, 4);

        given(reviewRepository.findMissionRating(20L)).willReturn(rating(null, 0L));
        Mission unrated = mission(20L, MissionStatus.ACTIVE);
        given(missionRepository.findWithPlaceById(20L)).willReturn(Optional.of(unrated));
        given(missionChecklistItemRepository.findAllByMissionOrderByItemTypeAscDisplayOrderAsc(unrated)).willReturn(List.of());
        given(externalRecordRepository.findFirstByMissionAndSourceUrlIsNotNullOrderByIdAsc(unrated)).willReturn(Optional.empty());

        MissionResDTO.Detail unratedDetail = missionQueryService.getDetail(MEMBER_ID, 20L);
        assertThat(unratedDetail).extracting(MissionResDTO.Detail::averageRating, MissionResDTO.Detail::reviewCount)
                .containsExactly(null, 0);
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
        given(reviewRepository.findMissionRating(10L)).willReturn(rating(null, 0L));

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
        given(reviewRepository.findMissionRating(10L)).willReturn(rating(null, 0L));

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
        given(reviewRepository.findMissionRating(10L)).willReturn(rating(null, 0L));

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
        given(reviewRepository.findMissionRating(10L)).willReturn(rating(null, 0L));

        MissionResDTO.Detail detail = missionQueryService.getDetail(MEMBER_ID, 10L);

        assertThat(detail.bucketSaved()).isTrue();
        assertThat(detail.bucketState()).isEqualTo(BucketSaveState.SAVED);
    }

    @Test
    @DisplayName("완료된 버킷만 있으면 bucketState는 COMPLETED_ONLY이고 bucketSaved는 false다.")
    void getDetail_bucketCompletedOnly() {
        Mission mission = mission(10L, MissionStatus.ACTIVE);
        given(missionRepository.findWithPlaceById(10L)).willReturn(Optional.of(mission));
        given(missionChecklistItemRepository.findAllByMissionOrderByItemTypeAscDisplayOrderAsc(mission)).willReturn(List.of());
        given(externalRecordRepository.findFirstByMissionAndSourceUrlIsNotNullOrderByIdAsc(mission)).willReturn(Optional.empty());
        given(bucketItemRepository.existsByMemberIdAndMissionIdAndStatusIn(MEMBER_ID, 10L, BucketStatus.ACTIVE_STATUSES))
                .willReturn(false);
        given(bucketItemRepository.existsByMemberIdAndMissionIdAndStatusIn(MEMBER_ID, 10L, List.of(BucketStatus.COMPLETED)))
                .willReturn(true);
        given(reviewRepository.findMissionRating(10L)).willReturn(rating(null, 0L));

        MissionResDTO.Detail detail = missionQueryService.getDetail(MEMBER_ID, 10L);

        assertThat(detail.bucketSaved()).isFalse();
        assertThat(detail.bucketState()).isEqualTo(BucketSaveState.COMPLETED_ONLY);
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

    @Test
    @DisplayName("근처 놀거리는 오늘 운영 중인 후보를 가까운 순으로 담는다.")
    void getNearby_sortsByDistance() {
        Mission far = nearbyMission(11L, nearbyPlace(2L, "먼 곳", 3.0), "먼 곳 소개");
        Mission near = nearbyMission(12L, nearbyPlace(3L, "가까운 곳", 0.5), "가까운 곳 소개");
        stubNearby(List.of(far, near));

        List<MissionResDTO.NearbyItem> result = missionQueryService.getNearby(1L);

        assertThat(result).extracting(MissionResDTO.NearbyItem::missionId).containsExactly(12L, 11L);
        assertThat(result).extracting(MissionResDTO.NearbyItem::distance).containsExactly(0.5, 3.0);
    }

    @Test
    @DisplayName("같은 장소에 미션이 여러 개면 미션 ID가 가장 작은 미션 하나만 담는다.")
    void getNearby_dedupsByPlace() {
        Place sevitseom = nearbyPlace(2L, "세빛섬", 2.0);
        stubNearby(List.of(nearbyMission(22L, sevitseom, "행사"), nearbyMission(21L, sevitseom, "상설")));

        List<MissionResDTO.NearbyItem> result = missionQueryService.getNearby(1L);

        assertThat(result).extracting(MissionResDTO.NearbyItem::missionId).containsExactly(21L);
    }

    @Test
    @DisplayName("미션 장소에서 1km 이내면 거리 문구, 아니면 미션 소개 문구를 담는다.")
    void getNearby_reason() {
        Mission near = nearbyMission(11L, nearbyPlace(2L, "가까운 곳", 0.9), "가까운 곳 소개");
        Mission far = nearbyMission(12L, nearbyPlace(3L, "먼 곳", 2.0), "먼 곳 소개");
        stubNearby(List.of(near, far));

        List<MissionResDTO.NearbyItem> result = missionQueryService.getNearby(1L);

        assertThat(result).extracting(MissionResDTO.NearbyItem::description)
                .containsExactly("미션 장소에서 가까운 곳이에요", "먼 곳 소개");
    }

    @Test
    @DisplayName("근처 놀거리 기준 미션이 노출할 수 없는 상태면 MISSION404_1을 던진다.")
    void getNearby_notVisible() {
        given(missionRepository.findWithPlaceById(1L)).willReturn(Optional.of(mission(1L, MissionStatus.HIDDEN)));

        assertThatThrownBy(() -> missionQueryService.getNearby(1L))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode").isEqualTo(MissionErrorCode.NOT_FOUND);
        verify(missionRepository, never()).findAllWithPlaceByIdIn(any());
    }

    @Test
    @DisplayName("맞춤템은 저장된 순서대로 최대 4개까지만 담는다.")
    void getEssentials_limit() {
        Mission mission = mission(10L, MissionStatus.ACTIVE);
        given(missionRepository.findWithPlaceById(10L)).willReturn(Optional.of(mission));
        given(missionChecklistItemRepository.findAllByMissionAndItemTypeOrderByDisplayOrderAsc(mission, ItemType.PREPARE))
                .willReturn(List.of(
                        item(mission, ItemType.PREPARE, "신분증", 1),
                        item(mission, ItemType.PREPARE, "수건", 2),
                        item(mission, ItemType.PREPARE, "여벌옷", 3),
                        item(mission, ItemType.PREPARE, "편한 신발", 4),
                        item(mission, ItemType.PREPARE, "물", 5)));

        List<MissionResDTO.ChecklistItem> result = missionQueryService.getEssentials(10L);

        assertThat(result).extracting(MissionResDTO.ChecklistItem::title)
                .containsExactly("신분증", "수건", "여벌옷", "편한 신발");
    }

    @Test
    @DisplayName("맞춤템 기준 미션이 노출할 수 없는 상태면 MISSION404_1을 던진다.")
    void getEssentials_notVisible() {
        given(missionRepository.findWithPlaceById(1L)).willReturn(Optional.of(mission(1L, MissionStatus.HIDDEN)));

        assertThatThrownBy(() -> missionQueryService.getEssentials(1L))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode").isEqualTo(MissionErrorCode.NOT_FOUND);
        verifyNoInteractions(missionChecklistItemRepository);
    }

    private void stubNearby(List<Mission> candidates) {
        given(missionRepository.findWithPlaceById(1L)).willReturn(Optional.of(mission(1L, MissionStatus.ACTIVE)));
        List<Long> ids = candidates.stream().map(Mission::getId).toList();
        given(missionRepository.findNearbyIds(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), eq(1L), eq(TODAY))).willReturn(ids);
        given(missionRepository.findAllWithPlaceByIdIn(ids)).willReturn(candidates);
    }

    // 기준 장소(서울숲)에서 북쪽으로 kmNorth만큼 떨어진 장소
    private Place nearbyPlace(Long id, String name, double kmNorth) {
        return Place.builder()
                .id(id)
                .name(name)
                .latitude(new BigDecimal("37.5444000").add(BigDecimal.valueOf(kmNorth * 0.009)))
                .longitude(new BigDecimal("127.0374000"))
                .build();
    }

    private Mission nearbyMission(Long id, Place place, String description) {
        return Mission.builder().id(id).place(place).description(description).status(MissionStatus.ACTIVE).build();
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
                .walkingLevel(WalkingLevel.HIGH)
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

    private com.kiri.server.domain.review.repository.ReviewRepository.MissionRating rating(Double average, long count) {
        return new com.kiri.server.domain.review.repository.ReviewRepository.MissionRating() {
            @Override
            public Double getAverageRating() {
                return average;
            }

            @Override
            public long getReviewCount() {
                return count;
            }
        };
    }
}
