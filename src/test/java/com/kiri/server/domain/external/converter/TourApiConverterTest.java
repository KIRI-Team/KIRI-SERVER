package com.kiri.server.domain.external.converter;

import com.kiri.server.domain.external.client.tourapi.dto.TourApiResDTO;
import com.kiri.server.domain.external.dto.NormalizedContent;
import com.kiri.server.domain.external.enums.ExternalSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TourApiConverterTest {

    private final JsonMapper jsonMapper = new JsonMapper();

    @Test
    @DisplayName("동기화 목록 항목을 신체계 지역·분류 코드로 읽는다.")
    void toSyncItem() {
        JsonNode item = jsonMapper.readTree("""
                {"contentid":"2786391","contenttypeid":"15","title":"한강 뚜벅뚜벅 축제",
                 "addr1":"서울특별시 영등포구 여의동로 330","addr2":"",
                 "mapx":"126.9327568","mapy":"37.5283169",
                 "firstimage":"http://tong.visitkorea.or.kr/cms/resource/1.jpg","cpyrhtDivCd":"Type1",
                 "areacode":"","cat1":"","lDongRegnCd":"11","lDongSignguCd":"560",
                 "lclsSystm1":"EV","lclsSystm2":"EV01","lclsSystm3":"EV010100",
                 "showflag":"1","modifiedtime":"20261010153000"}
                """);

        TourApiResDTO.SyncItem syncItem = TourApiConverter.toSyncItem(item);

        assertThat(syncItem.contentId()).isEqualTo("2786391");
        assertThat(syncItem.contentTypeId()).isEqualTo("15");
        assertThat(syncItem.address()).isEqualTo("서울특별시 영등포구 여의동로 330");
        assertThat(syncItem.addressDetail()).isNull();
        assertThat(syncItem.longitude()).isEqualByComparingTo(new BigDecimal("126.9327568"));
        assertThat(syncItem.latitude()).isEqualByComparingTo(new BigDecimal("37.5283169"));
        assertThat(syncItem.regionCode()).isEqualTo("11");
        assertThat(syncItem.lclsSystm1()).isEqualTo("EV");
        assertThat(syncItem.shown()).isTrue();
        assertThat(syncItem.modifiedAt()).isEqualTo(LocalDateTime.of(2026, 10, 10, 15, 30, 0));
        assertThat(syncItem.raw()).isSameAs(item);
    }

    @Test
    @DisplayName("showflag가 0이면 비표출 항목으로 읽는다.")
    void toSyncItem_hidden() {
        JsonNode item = jsonMapper.readTree("""
                {"contentid":"126508","contenttypeid":"12","title":"경복궁","showflag":"0"}
                """);

        assertThat(TourApiConverter.toSyncItem(item).shown()).isFalse();
    }

    @Test
    @DisplayName("좌표가 비었거나 0이면 위치 없음(null)으로 읽는다.")
    void toSyncItem_noLocation() {
        JsonNode item = jsonMapper.readTree("""
                {"contentid":"1","contenttypeid":"15","title":"행사","mapx":"0","mapy":"","modifiedtime":"잘못된값"}
                """);

        TourApiResDTO.SyncItem syncItem = TourApiConverter.toSyncItem(item);

        assertThat(syncItem.longitude()).isNull();
        assertThat(syncItem.latitude()).isNull();
        assertThat(syncItem.modifiedAt()).isNull();
    }

    @Test
    @DisplayName("행사 상세의 기간·장소·요금·프로그램을 미션 가공용 형태로 합친다.")
    void toNormalizedContent_event() {
        TourApiResDTO.SyncItem item = TourApiConverter.toSyncItem(jsonMapper.readTree("""
                {"contentid":"2786391","contenttypeid":"15","title":"한강 뚜벅뚜벅 축제",
                 "addr1":"서울특별시 영등포구 여의동로 330","addr2":"(여의도동)",
                 "mapx":"126.9327568","mapy":"37.5283169","lDongRegnCd":"11","lDongSignguCd":"560",
                 "lclsSystm1":"EV","lclsSystm2":"EV01","showflag":"1"}
                """));
        JsonNode common = jsonMapper.readTree("""
                {"contentid":"2786391","title":"한강 뚜벅뚜벅 축제","overview":"한강을 걷는 축제<br>가족 참여 환영"}
                """);
        JsonNode intro = jsonMapper.readTree("""
                {"contentid":"2786391","eventstartdate":"20261003","eventenddate":"20261005",
                 "eventplace":"여의도한강공원 일대","playtime":"10:00~18:00","usetimefestival":"무료",
                 "spendtimefestival":"","program":"1. 잠수교 걷기<br>2. 푸드트럭 존"}
                """);
        List<JsonNode> infos = List.of(
                jsonMapper.readTree("{\"infoname\":\"행사소개\",\"infotext\":\"긴 소개 문장\"}"),
                jsonMapper.readTree("{\"infoname\":\"행사 프로그램\",\"infotext\":\"버스킹 공연\"}"));

        NormalizedContent content = TourApiConverter.toNormalizedContent(item, common, intro, infos);

        assertThat(content.source()).isEqualTo(ExternalSource.TOUR_API);
        assertThat(content.externalId()).isEqualTo("2786391");
        assertThat(content.overview()).isEqualTo("한강을 걷는 축제\n가족 참여 환영");
        assertThat(content.address()).isEqualTo("서울특별시 영등포구 여의동로 330 (여의도동)");
        assertThat(content.regionCode()).isEqualTo("11560");
        assertThat(content.startDate()).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(content.endDate()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(content.venueName()).isEqualTo("여의도한강공원 일대");
        assertThat(content.usageTimeText()).isEqualTo("10:00~18:00");
        assertThat(content.feeText()).isEqualTo("무료");
        assertThat(content.durationText()).isNull();
        assertThat(content.programTexts()).containsExactly("1. 잠수교 걷기\n2. 푸드트럭 존", "버스킹 공연");
        assertThat(content.imageUrl()).isNull();
        assertThat(content.imageSource()).isNull();
    }

    @Test
    @DisplayName("음식점 대표 메뉴를 읽고 상세 응답이 없어도 목록 값으로 채운다.")
    void toNormalizedContent_restaurantWithoutDetails() {
        TourApiResDTO.SyncItem item = TourApiConverter.toSyncItem(jsonMapper.readTree("""
                {"contentid":"1","contenttypeid":"39","title":"황생가칼국수",
                 "addr1":"서울특별시 종로구 북촌로5길 78","mapx":"126.98","mapy":"37.58","lDongRegnCd":"11"}
                """));
        JsonNode intro = jsonMapper.readTree("""
                {"firstmenu":"칼국수","treatmenu":"칼국수, 만두","opentimefood":"11:00~21:30"}
                """);

        NormalizedContent content = TourApiConverter.toNormalizedContent(item, null, intro, null);

        assertThat(content.title()).isEqualTo("황생가칼국수");
        assertThat(content.overview()).isNull();
        assertThat(content.regionCode()).isEqualTo("11");
        assertThat(content.menuNames()).containsExactly("칼국수", "만두");
        assertThat(content.usageTimeText()).isEqualTo("11:00~21:30");
        assertThat(content.startDate()).isNull();
    }

    @Test
    @DisplayName("공통정보의 홈페이지 링크에서 원본 URL을 꺼낸다.")
    void toSourceUrl() {
        JsonNode anchor = jsonMapper.readTree("""
                {"homepage":"<a href=\\"https://www.museum.go.kr\\" target=\\"_blank\\">www.museum.go.kr</a>"}
                """);
        JsonNode plain = jsonMapper.readTree("""
                {"homepage":"http://hangang.seoul.go.kr 참고"}
                """);
        JsonNode domainOnly = jsonMapper.readTree("""
                {"homepage":"www.museum.go.kr"}
                """);

        assertThat(TourApiConverter.toSourceUrl(anchor)).isEqualTo("https://www.museum.go.kr");
        assertThat(TourApiConverter.toSourceUrl(plain)).isEqualTo("http://hangang.seoul.go.kr");
        assertThat(TourApiConverter.toSourceUrl(domainOnly)).isNull();
        assertThat(TourApiConverter.toSourceUrl(null)).isNull();
    }

    @Test
    @DisplayName("이미지는 공공누리 제1·3유형만 쓰고 출처에 유형을 함께 남긴다.")
    void imagePolicy() {
        NormalizedContent type1 = TourApiConverter.toNormalizedContent(imageItem("Type1"), null, null, null);
        NormalizedContent type3 = TourApiConverter.toNormalizedContent(imageItem("Type3"), null, null, null);
        NormalizedContent type2 = TourApiConverter.toNormalizedContent(imageItem("Type2"), null, null, null);
        NormalizedContent unknown = TourApiConverter.toNormalizedContent(imageItem(""), null, null, null);

        assertThat(type1.imageUrl()).isEqualTo("http://tong.visitkorea.or.kr/cms/resource/1.jpg");
        assertThat(type1.imageSource()).isEqualTo("한국관광공사 (공공누리 제1유형)");
        assertThat(type3.imageSource()).isEqualTo("한국관광공사 (공공누리 제3유형·변경금지)");
        assertThat(type2.imageUrl()).isNull();
        assertThat(type2.imageSource()).isNull();
        assertThat(unknown.imageUrl()).isNull();
    }

    private TourApiResDTO.SyncItem imageItem(String copyrightType) {
        return TourApiConverter.toSyncItem(jsonMapper.readTree("""
                {"contentid":"1","contenttypeid":"14","title":"가나아트센터",
                 "firstimage":"http://tong.visitkorea.or.kr/cms/resource/1.jpg","cpyrhtDivCd":"%s"}
                """.formatted(copyrightType)));
    }
}
