package com.kiri.server.domain.mission.generator;

import com.kiri.server.domain.external.dto.NormalizedContent;
import com.kiri.server.domain.external.enums.ReviewReason;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.ChecklistSource;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class MissionDraftFactoryTest {

    private static final Map<String, String> DEFAULT_LCLS2 = Map.of("39", "FD01", "14", "VE07");

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
                .extracting(MissionDraft.ChecklistDraft::itemType, MissionDraft.ChecklistDraft::source,
                        MissionDraft.ChecklistDraft::title, MissionDraft.ChecklistDraft::displayOrder)
                .containsExactly(
                        tuple(ItemType.STEP, ChecklistSource.ORIGIN, "잠수교 걷기", 1),
                        tuple(ItemType.STEP, ChecklistSource.ORIGIN, "푸드트럭 존 함께 즐기기", 2),
                        tuple(ItemType.STEP, ChecklistSource.ORIGIN, "버스킹 공연 관람하기", 3));
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
    @DisplayName("자연 관광지는 산책·야외 미션이 되고 근거가 없으면 카테고리별 KIRI 기본 활동과 기본 범위를 쓴다.")
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
                .extracting(MissionDraft.ChecklistDraft::source, MissionDraft.ChecklistDraft::title)
                .containsExactly(
                        tuple(ChecklistSource.KIRI, "함께 걸을 코스 정하기"),
                        tuple(ChecklistSource.KIRI, "서로 근황 이야기하기"));
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
    @DisplayName("음식점은 대표 메뉴를 원본 기반 필수 활동으로 먼저 쓴다.")
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
    @DisplayName("숙박·시장이 아닌 쇼핑·여행코스는 제외 업종 사유로 제외한다.")
    void ignoredTypes() {
        assertThat(factory.create(base("32", "어느 호텔").build()))
                .isEqualTo(new DraftResult.Ignored(ReviewReason.EXCLUDED_TYPE));
        assertThat(factory.create(base("38", "어느 시장").build())).isInstanceOf(DraftResult.Ignored.class);
        assertThat(factory.create(base("25", "어느 코스").build())).isInstanceOf(DraftResult.Ignored.class);
    }

    @Test
    @DisplayName("좌표가 없으면 NO_LOCATION 검수 대기로 보낸다.")
    void noLocation() {
        assertThat(factory.create(base("15", "행사").latitude(null).build()))
                .isEqualTo(new DraftResult.ReviewRequired(ReviewReason.NO_LOCATION));
        assertThat(factory.create(base("14", "박물관").longitude(null).build()))
                .isEqualTo(new DraftResult.ReviewRequired(ReviewReason.NO_LOCATION));
    }

    @Test
    @DisplayName("주소가 없어도 좌표가 유효하면 공개 대상이 된다.")
    void noAddress() {
        MissionDraft museum = ready(factory.create(base("14", "박물관").address(null).build()));
        MissionDraft festival = ready(factory.create(base("15", "주소 없는 축제").address(" ")
                .lclsSystm1("EV").lclsSystm2("EV01").lclsSystm3("EV010100")
                .startDate(LocalDate.of(2026, 10, 10)).endDate(LocalDate.of(2026, 10, 20))
                .build()));

        assertThat(museum.place().address()).isNull();
        assertThat(festival.description()).contains("주소 없는 축제에서 열려요.");
    }

    @Test
    @DisplayName("음식점·문화시설인데 분류 코드가 없으면 제외 업종인지 알 수 없어 CATEGORY_UNKNOWN으로 보낸다.")
    void unknownBusinessType() {
        assertThat(factory.create(base("39", "어느 식당").lclsSystm2(null).build()))
                .isEqualTo(new DraftResult.ReviewRequired(ReviewReason.CATEGORY_UNKNOWN));
        assertThat(factory.create(base("14", "어느 시설").lclsSystm2(null).build()))
                .isEqualTo(new DraftResult.ReviewRequired(ReviewReason.CATEGORY_UNKNOWN));
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
    @DisplayName("제목이 100자를 넘으면 원본 이름을 자르지 않고 TITLE_INVALID 검수로 보낸다.")
    void longTitle() {
        assertThat(factory.create(base("14", "가".repeat(120)).build()))
                .isEqualTo(new DraftResult.ReviewRequired(ReviewReason.TITLE_INVALID));
    }

    @Test
    @DisplayName("기간형 미션은 종료일 다음 날부터 기간이 지난 것으로 본다.")
    void isPeriodOver() {
        MissionDraft draft = ready(factory.create(base("15", "행사")
                .startDate(LocalDate.of(2026, 10, 3)).endDate(LocalDate.of(2026, 10, 5)).build()));

        assertThat(draft.isPeriodOver(LocalDate.of(2026, 10, 5))).isFalse();
        assertThat(draft.isPeriodOver(LocalDate.of(2026, 10, 6))).isTrue();
    }

    @Test
    @DisplayName("행사는 분류체계 코드로 전시회·공연·축제를 나눈다.")
    void eventByClassification() {
        MissionDraft exhibition = ready(factory.create(event("K-일러스트레이션페어", "EV03", "EV030100")));
        MissionDraft concert = ready(factory.create(event("가을밤 페스티벌", "EV02", "EV020700")));
        MissionDraft festival = ready(factory.create(event("K-푸드 페스티벌", "EV01", "EV010600")));

        assertThat(exhibition.category()).isEqualTo(Category.EXHIBITION);
        assertThat(concert.category()).isEqualTo(Category.PERFORMANCE);
        assertThat(festival.category()).isEqualTo(Category.POPUP);
    }

    @Test
    @DisplayName("역사·종교 관광지와 관광특구는 나들이 미션이 되고 원본 이름은 지역 표기까지 그대로 쓴다.")
    void historyAndDistrict() {
        MissionDraft temple = ready(factory.create(base("12", "화계사(서울)")
                .lclsSystm1("HS").lclsSystm2("HS03").lclsSystm3("HS030100").build()));
        MissionDraft district = ready(factory.create(base("12", "이태원 관광특구")
                .lclsSystm1("VE").lclsSystm2("VE05").lclsSystm3("VE050100").build()));

        assertThat(temple.category()).isEqualTo(Category.OUTDOOR);
        assertThat(temple.title()).isEqualTo("가족과 화계사(서울) 나들이 가기");
        assertThat(temple.place().name()).isEqualTo("화계사(서울)");
        assertThat(district.title()).isEqualTo("가족과 이태원 관광특구 나들이 가기");
    }

    @Test
    @DisplayName("문화시설은 공연시설이면 공연, 전시시설이면 전시 미션이 된다.")
    void cultureFacilityByClassification() {
        MissionDraft theater = ready(factory.create(base("14", "세종문화회관").lclsSystm2("VE06").build()));
        MissionDraft gallery = ready(factory.create(base("14", "가나아트센터").lclsSystm2("VE07").build()));

        assertThat(theater.category()).isEqualTo(Category.PERFORMANCE);
        assertThat(gallery.category()).isEqualTo(Category.EXHIBITION);
        assertThat(gallery.indoorOutdoor()).isEqualTo(IndoorOutdoor.INDOOR);
    }

    @Test
    @DisplayName("쇼핑 중 시장만 맛집 미션으로 받고 나머지 쇼핑은 제외한다.")
    void marketOnly() {
        MissionDraft market = ready(factory.create(base("38", "광장시장").lclsSystm2("SH06").build()));

        assertThat(market.category()).isEqualTo(Category.FOOD_CAFE);
        assertThat(market.place().placeType()).isEqualTo(PlaceType.TOURIST_SPOT);
        assertThat(factory.create(base("38", "롯데백화점").lclsSystm2("SH01").build()))
                .isInstanceOf(DraftResult.Ignored.class);
    }

    @Test
    @DisplayName("주점·교통시설·연회장·카지노는 가족 미션 대상에서 제외한다.")
    void ignoredClassifications() {
        assertThat(factory.create(base("39", "어느 펍").lclsSystm2("FD04").build())).isInstanceOf(DraftResult.Ignored.class);
        assertThat(factory.create(base("12", "서울역").lclsSystm2("VE11").build())).isInstanceOf(DraftResult.Ignored.class);
        assertThat(factory.create(base("14", "어느 연회장").lclsSystm2("VE08").build())).isInstanceOf(DraftResult.Ignored.class);
        assertThat(factory.create(base("12", "어느 카지노").lclsSystm2("VE12").lclsSystm3("VE120200").build()))
                .isInstanceOf(DraftResult.Ignored.class);
    }

    @Test
    @DisplayName("원본 활동이 1개뿐이면 카테고리 기본 활동 1개를 더해 2개를 채운다.")
    void checklist_fillsWithDefault() {
        MissionDraft draft = ready(factory.create(base("39", "황생가칼국수").menuNames(List.of("칼국수")).build()));

        assertThat(draft.checklist())
                .extracting(MissionDraft.ChecklistDraft::source, MissionDraft.ChecklistDraft::title)
                .containsExactly(
                        tuple(ChecklistSource.ORIGIN, "칼국수 함께 맛보기"),
                        tuple(ChecklistSource.KIRI, "각자 먹고 싶은 메뉴 고르기"));
    }

    @Test
    @DisplayName("카테고리마다 서로 다른 KIRI 기본 활동을 쓴다.")
    void checklist_defaultsByCategory() {
        assertThat(titles(ready(factory.create(base("14", "국립중앙박물관").build()))))
                .containsExactly("마음에 드는 작품 고르기", "서로 감상 이야기하기");
        assertThat(titles(ready(factory.create(event("가을밤 페스티벌", "EV02", "EV020700")))))
                .containsExactly("관람 전 기대되는 점 이야기하기", "가장 기억에 남는 장면 이야기하기");
        assertThat(titles(ready(factory.create(event("K-푸드 페스티벌", "EV01", "EV010600")))))
                .containsExactly("함께 참여할 부스나 프로그램 고르기", "가장 재미있었던 활동 공유하기");
        assertThat(titles(ready(factory.create(base("28", "한강 카약 체험").build()))))
                .containsExactly("함께할 체험 고르기", "가장 재미있었던 순간 공유하기");
    }

    @Test
    @DisplayName("제목에 명확한 취소 표시가 있으면 외부 취소 사유로 제외한다.")
    void cancelledByTitle() {
        assertThat(factory.create(event("[취소] 한강 불꽃축제", "EV01", "EV010100")))
                .isEqualTo(new DraftResult.Ignored(ReviewReason.EXTERNAL_CANCELLED));
        assertThat(factory.create(event("한강 축제(행사취소)", "EV01", "EV010100")))
                .isEqualTo(new DraftResult.Ignored(ReviewReason.EXTERNAL_CANCELLED));
        assertThat(factory.create(event("우천 시 취소될 수 있는 축제", "EV01", "EV010100")))
                .isInstanceOf(DraftResult.Ready.class);
    }

    @Test
    @DisplayName("서울 범위를 벗어난 좌표는 위치를 특정할 수 없는 것으로 보고 검수로 보낸다.")
    void outOfServiceArea() {
        assertThat(factory.create(base("14", "부산시립미술관")
                .latitude(new BigDecimal("35.1667")).longitude(new BigDecimal("129.1361")).build()))
                .isEqualTo(new DraftResult.ReviewRequired(ReviewReason.NO_LOCATION));
    }

    private List<String> titles(MissionDraft draft) {
        return draft.checklist().stream().map(MissionDraft.ChecklistDraft::title).toList();
    }

    private NormalizedContent event(String title, String lclsSystm2, String lclsSystm3) {
        return base("15", title)
                .lclsSystm1("EV").lclsSystm2(lclsSystm2).lclsSystm3(lclsSystm3)
                .startDate(LocalDate.of(2026, 10, 10)).endDate(LocalDate.of(2026, 10, 20))
                .build();
    }

    private NormalizedContent.NormalizedContentBuilder base(String contentTypeId, String title) {
        return NormalizedContent.builder()
                .contentTypeId(contentTypeId)
                .lclsSystm2(DEFAULT_LCLS2.get(contentTypeId))
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
