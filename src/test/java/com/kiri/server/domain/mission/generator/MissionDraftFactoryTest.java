package com.kiri.server.domain.mission.generator;

import com.kiri.server.domain.external.dto.NormalizedContent;
import com.kiri.server.domain.external.enums.ReviewReason;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.mission.enums.ItemType;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.mission.generator.dto.DraftResult;
import com.kiri.server.domain.mission.generator.dto.MissionDraft;
import com.kiri.server.domain.place.enums.PlaceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class MissionDraftFactoryTest {

    private final MissionDraftFactory factory = new MissionDraftFactory(new RuleOnlyTitlePolisher());

    @Test
    @DisplayName("축제는 기간형 팝업·행사 미션이 되고 행사 시간과 공식 프로그램을 담는다.")
    void festival() {
        NormalizedContent content = base("15", "한강 뚜벅뚜벅 축제")
                .lclsSystm1("EV").lclsSystm2("EV01")
                .startDate(LocalDate.of(2026, 10, 3)).endDate(LocalDate.of(2026, 10, 5))
                .venueName("여의도한강공원 일대")
                .usageTimeText("10:00~18:00")
                .feeText("무료")
                .overview("한강을 걸으며 즐기는 축제")
                .programTexts(List.of("1. 잠수교 걷기\n2. 푸드트럭 존\n3. 버스킹 공연\n4. 플리마켓"))
                .build();

        MissionDraft draft = ready(factory.create(content));

        assertThat(draft.title()).isEqualTo("한강 뚜벅뚜벅 축제 방문하기");
        assertThat(draft.category()).isEqualTo(Category.POPUP);
        assertThat(draft.missionType()).isEqualTo(MissionType.PERIOD);
        assertThat(draft.startDate()).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(draft.openingTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(draft.price()).isZero();
        assertThat(draft.description()).isEqualTo("한강을 걸으며 즐기는 축제");
        assertThat(draft.place().name()).isEqualTo("여의도한강공원 일대");
        assertThat(draft.place().placeType()).isEqualTo(PlaceType.EVENT_VENUE);
        assertThat(draft.place().openingTime()).isNull();
        assertThat(draft.checklist())
                .extracting(MissionDraft.ChecklistDraft::itemType, MissionDraft.ChecklistDraft::title,
                        MissionDraft.ChecklistDraft::displayOrder)
                .containsExactly(
                        tuple(ItemType.STEP, "잠수교 걷기", 1),
                        tuple(ItemType.STEP, "푸드트럭 존", 2),
                        tuple(ItemType.STEP, "버스킹 공연", 3));
    }

    @Test
    @DisplayName("공연 키워드가 있는 행사는 공연 미션이 되고 소요시간과 최저가를 읽는다.")
    void performance() {
        NormalizedContent content = base("15", "가을밤 음악회")
                .lclsSystm1("EV")
                .startDate(LocalDate.of(2026, 11, 1)).endDate(LocalDate.of(2026, 11, 1))
                .feeText("R석 50,000원 / S석 30,000원")
                .durationText("약 2시간")
                .build();

        MissionDraft draft = ready(factory.create(content));

        assertThat(draft.title()).isEqualTo("가을밤 음악회 관람하기");
        assertThat(draft.category()).isEqualTo(Category.PERFORMANCE);
        assertThat(draft.price()).isEqualTo(30000);
        assertThat(draft.durationMinMinutes()).isEqualTo(120);
        assertThat(draft.durationMaxMinutes()).isEqualTo(120);
        assertThat(draft.description()).isEqualTo("2026.11.1 서울특별시 종로구 사직로 161에서 열려요.");
    }

    @Test
    @DisplayName("자연 관광지는 산책·야외 미션이 되고 근거가 없으면 범용 활동과 기본 범위를 쓴다.")
    void outdoorSpot() {
        NormalizedContent content = base("12", "남산공원").lclsSystm1("NA").lclsSystm2("NA04").build();

        MissionDraft draft = ready(factory.create(content));

        assertThat(draft.title()).isEqualTo("가족과 남산공원 나들이 가기");
        assertThat(draft.category()).isEqualTo(Category.OUTDOOR);
        assertThat(draft.missionType()).isEqualTo(MissionType.PERMANENT);
        assertThat(draft.indoorOutdoor()).isEqualTo(IndoorOutdoor.OUTDOOR);
        assertThat(draft.price()).isNull();
        assertThat(draft.durationMinMinutes()).isLessThan(draft.durationMaxMinutes());
        assertThat(draft.description()).isEqualTo("남산공원에서 즐기는 산책·야외 활동이에요.");
        assertThat(draft.checklist())
                .extracting(MissionDraft.ChecklistDraft::title)
                .containsExactly("가족 사진 남기기", "주변 함께 둘러보기");
    }

    @Test
    @DisplayName("문화시설은 상시 전시 미션이 되고 운영시간은 장소에 둔다.")
    void cultureFacility() {
        NormalizedContent content = base("14", "국립중앙박물관")
                .feeText("무료").usageTimeText("10:00~18:00").build();

        MissionDraft draft = ready(factory.create(content));

        assertThat(draft.title()).isEqualTo("국립중앙박물관 관람하기");
        assertThat(draft.indoorOutdoor()).isEqualTo(IndoorOutdoor.INDOOR);
        assertThat(draft.openingTime()).isNull();
        assertThat(draft.place().openingTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(draft.place().closingTime()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    @DisplayName("음식점은 대표 메뉴로 필수 활동을 만든다.")
    void restaurant() {
        NormalizedContent content = base("39", "황생가칼국수").menuNames(List.of("칼국수", "만두")).build();

        MissionDraft draft = ready(factory.create(content));

        assertThat(draft.category()).isEqualTo(Category.FOOD_CAFE);
        assertThat(draft.checklist())
                .extracting(MissionDraft.ChecklistDraft::title)
                .containsExactly("칼국수 함께 맛보기", "만두 함께 맛보기");
    }

    @Test
    @DisplayName("체험명이 분명하면 체험하기, 아니면 방문하기로 만든다.")
    void experienceTitle() {
        MissionDraft kayak = ready(factory.create(base("28", "한강 카약 체험").build()));
        MissionDraft camping = ready(factory.create(base("28", "난지 캠핑장").build()));

        assertThat(kayak.title()).isEqualTo("가족과 한강 카약 체험하기");
        assertThat(camping.title()).isEqualTo("난지 캠핑장 방문하기");
    }

    @Test
    @DisplayName("숙박·쇼핑·여행코스는 미션 대상이 아니다.")
    void ignoredTypes() {
        assertThat(factory.create(base("32", "어느 호텔").build())).isInstanceOf(DraftResult.Ignored.class);
        assertThat(factory.create(base("38", "어느 시장").build())).isInstanceOf(DraftResult.Ignored.class);
        assertThat(factory.create(base("25", "어느 코스").build())).isInstanceOf(DraftResult.Ignored.class);
    }

    @Test
    @DisplayName("좌표나 주소가 없으면 NO_LOCATION 검수 대기로 보낸다.")
    void noLocation() {
        assertThat(factory.create(base("15", "행사").latitude(null).build()))
                .isEqualTo(new DraftResult.ReviewRequired(ReviewReason.NO_LOCATION));
        assertThat(factory.create(base("14", "박물관").address(" ").build()))
                .isEqualTo(new DraftResult.ReviewRequired(ReviewReason.NO_LOCATION));
    }

    @Test
    @DisplayName("관광지 분류 코드가 없으면 카테고리를 만들어내지 않고 CATEGORY_UNKNOWN으로 보낸다.")
    void categoryUnknown() {
        assertThat(factory.create(base("12", "어딘가").build()))
                .isEqualTo(new DraftResult.ReviewRequired(ReviewReason.CATEGORY_UNKNOWN));
    }

    @Test
    @DisplayName("기간이 없거나 잘못된 행사는 MISSING_REQUIRED 검수 대기로 보낸다.")
    void eventWithoutPeriod() {
        NormalizedContent noPeriod = base("15", "날짜 미정 행사").build();
        NormalizedContent reversed = base("15", "잘못된 기간")
                .startDate(LocalDate.of(2026, 10, 5)).endDate(LocalDate.of(2026, 10, 3)).build();

        assertThat(factory.create(noPeriod)).isEqualTo(new DraftResult.ReviewRequired(ReviewReason.MISSING_REQUIRED));
        assertThat(factory.create(reversed)).isEqualTo(new DraftResult.ReviewRequired(ReviewReason.MISSING_REQUIRED));
    }

    @Test
    @DisplayName("제목이 100자를 넘으면 원본 이름을 줄여 컬럼 길이에 맞춘다.")
    void longTitle() {
        MissionDraft draft = ready(factory.create(base("14", "가".repeat(120)).build()));

        assertThat(draft.title()).hasSize(100).endsWith(" 관람하기");
    }

    @Test
    @DisplayName("기간형 미션은 종료일 다음 날부터 기간이 지난 것으로 본다.")
    void isPeriodOver() {
        MissionDraft draft = ready(factory.create(base("15", "행사")
                .startDate(LocalDate.of(2026, 10, 3)).endDate(LocalDate.of(2026, 10, 5)).build()));

        assertThat(draft.isPeriodOver(LocalDate.of(2026, 10, 5))).isFalse();
        assertThat(draft.isPeriodOver(LocalDate.of(2026, 10, 6))).isTrue();
    }

    private NormalizedContent.NormalizedContentBuilder base(String contentTypeId, String title) {
        return NormalizedContent.builder()
                .contentTypeId(contentTypeId)
                .externalId("1")
                .title(title)
                .address("서울특별시 종로구 사직로 161")
                .latitude(new BigDecimal("37.5796"))
                .longitude(new BigDecimal("126.9770"))
                .regionCode("11110")
                .imageUrl("http://tong.visitkorea.or.kr/cms/resource/1.jpg")
                .imageSource("한국관광공사");
    }

    private MissionDraft ready(DraftResult result) {
        assertThat(result).isInstanceOf(DraftResult.Ready.class);
        return ((DraftResult.Ready) result).draft();
    }
}
